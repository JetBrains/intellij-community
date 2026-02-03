// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker;
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls;
import com.intellij.ide.plugins.newui.PluginDependencyModel;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUiModelAdapter;
import com.intellij.internal.statistic.DeviceIdManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.ide.plugins.BrokenPluginFileKt.isBrokenPlugin;

public final class PluginDownloader {
  private static final Logger LOG = Logger.getInstance(PluginDownloader.class);

  private final PluginId myPluginId;
  private final @Nullable String myPluginName;
  private final @Nullable String myProductCode;
  private final @Nullable Date myReleaseDate;
  private final int myReleaseVersion;
  private final boolean myLicenseOptional;
  private final String myDescription;
  private final List<PluginDependencyModel> myDependencies;

  private final String myPluginUrl;
  private final BuildNumber myBuildNumber;

  private final Consumer<@NotNull @NotificationContent String> myErrorsConsumer;
  private final @Nullable MarketplacePluginDownloadService myDownloadService;

  private @NlsSafe String myPluginVersion;
  private final PluginUiModel myModel;
  private IdeaPluginDescriptor myDescriptor;
  private Path myFile;
  private Path myOldFile;
  private boolean myShownErrors;

  private PluginDownloader(
    PluginUiModel model,
    String pluginUrl,
    @Nullable BuildNumber buildNumber,
    Consumer<@NotNull @NotificationContent String> errorsConsumer,
    @Nullable MarketplacePluginDownloadService service
  ) {
    myPluginId = model.getPluginId();
    myPluginName = model.getName();
    myProductCode = model.getProductCode();
    myReleaseDate = model.getReleaseDate() != null ? Date.from(Instant.ofEpochMilli(model.getReleaseDate())) : null;
    myReleaseVersion = model.getReleaseVersion();
    myLicenseOptional = model.isLicenseOptional();
    myDescription = model.getDescription();
    myDependencies = model.getDependencies();

    myPluginUrl = pluginUrl;
    myBuildNumber = buildNumber;

    myPluginVersion = model.getVersion();
    myDescriptor = model.getDescriptor();
    myModel = model;
    myErrorsConsumer = errorsConsumer;
    myDownloadService = service;
  }

  public @NotNull PluginDownloader withErrorsConsumer(@NotNull Consumer<@NotNull @NotificationContent String> errorsConsumer) {
    return new PluginDownloader(myModel, myPluginUrl, myBuildNumber, errorsConsumer, myDownloadService);
  }

  public @NotNull PluginDownloader withDownloadService(@Nullable MarketplacePluginDownloadService downloadService) {
    return new PluginDownloader(myModel, myPluginUrl, myBuildNumber, myErrorsConsumer, downloadService);
  }

  /** @deprecated Use {@link #getId()} */
  @Deprecated(forRemoval = true)
  public @NotNull String getPluginId() {
    return myPluginId.getIdString();
  }

  public @NotNull PluginId getId() {
    return myPluginId;
  }

  public @NlsSafe String getPluginVersion() {
    return myPluginVersion;
  }

  public @NlsSafe @NotNull String getPluginName() {
    return myPluginName != null ? myPluginName : myPluginId.getIdString();
  }

  public @Nullable String getProductCode() {
    return myProductCode;
  }

  public @Nullable Date getReleaseDate() {
    return myReleaseDate;
  }

  public int getReleaseVersion() {
    return myReleaseVersion;
  }

  public boolean isFromMarketplace() {
    try {
      return Objects.equals(new URI(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST).getHost(), new URI(myPluginUrl).getHost());
    }
    catch (URISyntaxException ignored) {
      return false;
    }
  }

  public boolean isLicenseOptional() {
    return myLicenseOptional;
  }

  public @Nullable BuildNumber getBuildNumber() {
    return myBuildNumber;
  }

  public @NotNull IdeaPluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  // TODO this method is nullable for extra safety, can probably be dropped later with some caution
  @ApiStatus.Internal
  public @Nullable PluginUiModel getUiModel() {
    return myModel;
  }

  public @NotNull Path getFilePath() throws IOException {
    if (myFile == null) throw new IOException("Plugin '" + getPluginName() + "' was not successfully downloaded");
    return myFile;
  }

  public boolean isShownErrors() {
    return myShownErrors;
  }

  public boolean prepareToInstall(@Nullable ProgressIndicator indicator) throws IOException {
    ThreadingAssertions.assertBackgroundThread();
    myShownErrors = false;

    if (myFile != null) {
      var actualDescriptor = loadDescriptorFromArtifact();
      if (actualDescriptor == null) {
        reportError(IdeBundle.message("error.descriptor.load.failed", myFile));
        return false;
      }
      myDescriptor = actualDescriptor;
      return true;
    }

    var descriptor = (IdeaPluginDescriptor)null;

    if (PluginManagerCore.isPluginInstalled(myPluginId)) {
      descriptor = PluginManagerCore.getPlugin(myPluginId);
      LOG.assertTrue(descriptor != null);

      if (myPluginVersion != null) {
        int result = compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor);
        if (result < 0 && isDowngradeAllowed(descriptor)) {
          LOG.info("Preparing to downgrade plugin '" + myPluginId + "' : " + myPluginVersion + " -> " + descriptor.getVersion());
        }
        else if (result <= 0) {
          LOG.info("Preparing: plugin " + myPluginId + ": current version (max) " + myPluginVersion);
          return false;
        }
      }

      myOldFile = descriptor.isBundled() ? null : descriptor.getPluginPath();
    }

    try {
      myFile = tryDownloadPlugin(indicator);
    }
    catch (IOException e) {
      LOG.info(e);
      var message = e.getMessage();
      reportError(message != null ? message : IdeBundle.message("unknown.error"));
      return false;
    }

    var loaded = LoadingState.COMPONENTS_LOADED.isOccurred();  // plugins can be requested during initial IDE setup
    if (loaded && !PluginSignatureChecker.verifyIfRequired(myDescriptor, myFile, isFromMarketplace(), true)) {
      myShownErrors = true;
      return false;
    }

    var actualDescriptor = loadDescriptorFromArtifact();
    if (actualDescriptor == null) {
      reportError(IdeBundle.message("error.downloaded.descriptor.load.failed"));
      return false;
    }

    if (loaded && InstalledPluginsState.getInstance().wasUpdated(actualDescriptor.getPluginId())) {
      reportError(IdeBundle.message("error.pending.update", getPluginName()));
      return false;
    }

    myPluginVersion = actualDescriptor.getVersion();

    if (descriptor != null) {
      int result = compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor);
      if (result < 0 && isDowngradeAllowed(descriptor)) {
        LOG.info("Downgrading plugin '" + myPluginId + "' : " + myPluginVersion + " -> " + descriptor.getVersion());
      }
      else if (result <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        reportError(IdeBundle.message("error.older.update", myPluginVersion, descriptor.getVersion()));
        return false;
      }
    }

    myDescriptor = actualDescriptor;

    var buildNumber = myBuildNumber != null ? myBuildNumber : PluginManagerCore.getBuildNumber();
    var incompatibleError = PluginManagerCore.checkBuildNumberCompatibility(actualDescriptor, buildNumber);
    if (incompatibleError != null) {
      LOG.info("Plugin " + myPluginId + " is incompatible with current installation " +
               "(since:" + actualDescriptor.getSinceBuild() + " until:" + actualDescriptor.getUntilBuild() + ")");
      reportError(IdeBundle.message("error.incompatible.update", XmlStringUtil.escapeString(incompatibleError.getDetailedMessage())));
      return false;
    }

    return true;
  }

  private boolean isDowngradeAllowed(IdeaPluginDescriptor localDescriptor) {
    return PluginManagementPolicy.getInstance().isDowngradeAllowed(localDescriptor, myDescriptor);
  }

  private @Nullable IdeaPluginDescriptorImpl loadDescriptorFromArtifact() throws IOException {
    ThreadingAssertions.assertBackgroundThread();
    if (myBuildNumber == null) {
      return PluginDescriptorLoader.loadDescriptorFromArtifact(getFilePath(), null);
    }
    else {
      return PluginDescriptorLoader.readBasicDescriptorDataFromArtifact(getFilePath());
    }
  }

  private void reportError(@NotificationContent String errorMessage) {
    LOG.info("PluginDownloader error: " + errorMessage);
    myShownErrors = true;
    myErrorsConsumer.accept(IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage));
  }

  public void install() throws IOException {
    PluginInstaller.installAfterRestartAndKeepIfNecessary(myDescriptor, getFilePath(), myOldFile);

    if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
      var isInstalled = PluginManagerCore.isPluginInstalled(myDescriptor.getPluginId());
      InstalledPluginsState.getInstance().onPluginInstall(myDescriptor, isInstalled, true);
    }
    else {
      InstalledPluginsState.addPreInstalledPlugin(myDescriptor);
    }
  }

  public boolean installDynamically(@Nullable JComponent ownerComponent) throws IOException {
    assert myDescriptor instanceof IdeaPluginDescriptorImpl;
    var descriptor = (IdeaPluginDescriptorImpl)myDescriptor;

    var appliedWithoutRestart =
      DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor) &&
      (myOldFile == null || unloadDescriptorById(descriptor.getPluginId())) &&
      PluginInstaller.installAndLoadDynamicPlugin(getFilePath(), ownerComponent, descriptor);

    if (!appliedWithoutRestart) {
      install();
    }

    return appliedWithoutRestart;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public @NotNull Path tryDownloadPlugin(@Nullable ProgressIndicator indicator) throws IOException {
    ThreadingAssertions.assertBackgroundThread();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText2(IdeBundle.message("progress.downloading.plugin", getPluginName()));
    }

    LOG.info("downloading plugin " + myPluginName + "(" + myPluginId + ") version " + myPluginVersion + " from " + myPluginUrl);
    var downloader = myDownloadService != null ? myDownloadService : new MarketplacePluginDownloadService();
    return myOldFile != null ?
           downloader.downloadPluginViaBlockMap(myPluginUrl, myOldFile, indicator) :
           downloader.downloadPlugin(myPluginUrl, indicator);
  }

  @ApiStatus.Internal
  public boolean checkPluginCanBeDownloaded(@Nullable ProgressIndicator indicator) {
    var downloader = myDownloadService != null ? myDownloadService : new MarketplacePluginDownloadService();
    return downloader.checkPluginCanBeDownloaded(myPluginUrl, indicator);
  }

  public @NotNull PluginNode toPluginNode() {
    var descriptor = getDescriptor();
    if (descriptor instanceof PluginNode) {
      return (PluginNode)descriptor;
    }

    var node = new PluginNode(getId());
    node.setName(getPluginName());
    node.setProductCode(getProductCode());
    node.setReleaseDate(getReleaseDate());
    node.setReleaseVersion(getReleaseVersion());
    node.setLicenseOptional(isLicenseOptional());
    node.setVersion(getPluginVersion());
    node.setDownloadUrl(myPluginUrl);
    node.setDependencies(ContainerUtil.map(myDependencies, dep -> new PluginDependencyImpl(dep.getPluginId(), null, dep.isOptional())));
    node.setDescription(myDescription);
    return node;
  }

  // This is a hacky way to get around the fact that prepareToInstall does more than it should. FIXME
  void setOldFile(@Nullable Path oldFile) {
    myOldFile = oldFile;
  }

  public static String getMarketplaceDownloadsUUID() {
    try {
      return DeviceIdManager.getOrGenerateId(new DeviceIdManager.DeviceIdToken() {
      }, "MarketplaceDownloads");
    }
    catch (DeviceIdManager.InvalidDeviceIdTokenException e) {
      return "";
    }
  }

  public static void showErrorDialog(@NotNull @NotificationContent String text) {
    var app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      app.invokeLater(() -> Messages.showErrorDialog(text, IdeBundle.message("title.plugin.installation")), ModalityState.any());
    }
  }

  public static int compareVersionsSkipBrokenAndIncompatible(String newPluginVersion, @NotNull IdeaPluginDescriptor existingPlugin) {
    return compareVersionsSkipBrokenAndIncompatible(newPluginVersion, existingPlugin, null);
  }

  public static int compareVersionsSkipBrokenAndIncompatible(
    String newPluginVersion,
    @NotNull IdeaPluginDescriptor existingPlugin,
    @Nullable BuildNumber newBuildNumber
  ) {
    int state = VersionComparatorUtil.compare(newPluginVersion, existingPlugin.getVersion());
    if (state < 0 && (isBrokenPlugin(existingPlugin) || PluginManagerCore.isIncompatible(existingPlugin, newBuildNumber))) {
      state = 1;
    }
    return state;
  }

  public static @NotNull PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor) throws IOException {
    return createDownloader(descriptor, null, null);
  }

  private static boolean unloadDescriptorById(PluginId pluginId) {
    var descriptor = PluginManagerCore.findPlugin(pluginId);
    if (descriptor == null) {
      return false;
    }
    var pluginDescriptor = IdeaPluginDescriptorImplKt.getMainDescriptor(descriptor);
    if (!DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)) {
      return false;
    }
    var options = new DynamicPlugins.UnloadPluginOptions().withDisable(false).withUpdate(true).withWaitForClassloaderUnload(true);
    return DynamicPlugins.INSTANCE.unloadPlugin(pluginDescriptor, options);
  }

  @ApiStatus.Internal
  public static @NotNull PluginDownloader createDownloader(
    @NotNull PluginUiModel pluginUiModel,
    @Nullable String host, // FIXME this should come from pluginUiModel (there is repositoryName)
    @Nullable BuildNumber buildNumber
  ) throws IOException {
    return createDownloader(pluginUiModel, host, buildNumber, pluginUiModel.getDownloadUrl(), pluginUiModel.isFromMarketplace());
  }

  public static @NotNull PluginDownloader createDownloader(
    @NotNull IdeaPluginDescriptor descriptor,
    @Nullable String host,
    @Nullable BuildNumber buildNumber
  ) throws IOException {
    var isFromCustomRepo = descriptor instanceof PluginNode;
    var downloadUrl = isFromCustomRepo ? ((PluginNode)descriptor).getDownloadUrl() : null;
    return createDownloader(new PluginUiModelAdapter(descriptor), host, buildNumber, downloadUrl, isFromCustomRepo);
  }

  private static @NotNull PluginDownloader createDownloader(
    @NotNull PluginUiModel descriptor,
    @Nullable String host,
    @Nullable BuildNumber buildNumber,
    @Nullable String downloadUrl,
    boolean isFromCustomRepo
  ) throws IOException {
    var url =
      isFromCustomRepo && host != null && downloadUrl != null ?
      toAbsoluteUrl(host, downloadUrl) :
      MarketplaceUrls.getPluginDownloadUrl(
        descriptor, getMarketplaceDownloadsUUID(), buildNumber, PluginManagerCore.getPlugin(descriptor.getPluginId())
      );
    return new PluginDownloader(descriptor, url, buildNumber, PluginDownloader::showErrorDialog, null);
  }

  private static String toAbsoluteUrl(String host, String downloadUrl) throws IOException {
    if (downloadUrl.indexOf(' ') >= 0) {
      downloadUrl = downloadUrl.replace(" ", "%20");  // a workaround for clumsily encoded custom plugin repositories
    }
    try {
      return new URI(host).resolve(new URI(downloadUrl)).toASCIIString();
    }
    catch (URISyntaxException e) {
      throw new IOException('[' + host + ", " + downloadUrl + ']', e);
    }
  }
}
