// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.internal.statistic.DeviceIdManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.Urls;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class PluginDownloader {

  private static final Logger LOG = Logger.getInstance(PluginDownloader.class);

  private final @NotNull PluginId myPluginId;
  private final @Nullable String myPluginName;
  private final @Nullable String myProductCode;
  private final Date myReleaseDate;
  private final int myReleaseVersion;
  private final boolean myLicenseOptional;
  private final String myDescription;
  private final @NotNull List<? extends IdeaPluginDependency> myDependencies;

  private final @NotNull String myPluginUrl;
  private final BuildNumber myBuildNumber;

  private final @NotNull Consumer<@NotNull @NlsContexts.NotificationContent String> myErrorsConsumer;
  private final @Nullable MarketplacePluginDownloadService myDownloadService;

  private @NlsSafe String myPluginVersion;
  private IdeaPluginDescriptor myDescriptor;
  private File myFile;
  private Path myOldFile;

  private boolean myShownErrors;

  private PluginDownloader(@NotNull IdeaPluginDescriptor descriptor,
                           @NotNull String pluginUrl,
                           @Nullable BuildNumber buildNumber,
                           @NotNull Consumer<@NotNull @NlsContexts.NotificationContent String> errorsConsumer,
                           @Nullable MarketplacePluginDownloadService service) {
    myPluginId = descriptor.getPluginId();
    myPluginName = descriptor.getName();
    myProductCode = descriptor.getProductCode();
    myReleaseDate = descriptor.getReleaseDate();
    myReleaseVersion = descriptor.getReleaseVersion();
    myLicenseOptional = descriptor.isLicenseOptional();
    myDescription = descriptor.getDescription();
    myDependencies = descriptor.getDependencies();

    myPluginUrl = pluginUrl;
    myBuildNumber = buildNumber;

    myPluginVersion = descriptor.getVersion();
    myDescriptor = descriptor;
    myErrorsConsumer = errorsConsumer;
    myDownloadService = service;
  }

  public @NotNull PluginDownloader withErrorsConsumer(@NotNull Consumer<@NotNull @NlsContexts.NotificationContent String> errorsConsumer) {
    return new PluginDownloader(myDescriptor,
                                myPluginUrl,
                                myBuildNumber,
                                errorsConsumer,
                                myDownloadService);
  }

  public @NotNull PluginDownloader withDownloadService(@Nullable MarketplacePluginDownloadService downloadService) {
    return new PluginDownloader(myDescriptor,
                                myPluginUrl,
                                myBuildNumber,
                                myErrorsConsumer,
                                downloadService);
  }

  /**
   * @deprecated Use {@link #getId()}
   */
  @Deprecated
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

  public Date getReleaseDate() {
    return myReleaseDate;
  }

  public int getReleaseVersion() {
    return myReleaseVersion;
  }

  public boolean isFromMarketplace() {
    try {
      URL pluginURL = new URL(myPluginUrl);
      URL defaultPluginsHost = new URL(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      return pluginURL.getHost().equals(defaultPluginsHost.getHost());
    }
    catch (MalformedURLException ignored) {
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

  public @NotNull Path getFilePath() throws IOException {
    if (myFile != null) {
      return myFile.toPath();
    }
    else {
      throw new IOException("Plugin '" + getPluginName() + "' was not successfully downloaded");
    }
  }

  public boolean isShownErrors() {
    return myShownErrors;
  }

  /**
   * @deprecated Please use {@link PluginDownloader#withErrorsConsumer(Consumer)} to set the errors' consumer,
   * and {@link PluginDownloader#getDescriptor()} to get the actual descriptor instance.
   */
  @Deprecated(forRemoval = true)
  @RequiresBackgroundThread
  public @Nullable IdeaPluginDescriptorImpl prepareToInstallAndLoadDescriptor(@NotNull ProgressIndicator indicator,
                                                                              boolean showMessageOnError) throws IOException {
    PluginDownloader downloader = showMessageOnError ?
                                  this :
                                  withErrorsConsumer(__ -> {
                                  });

    return downloader.prepareToInstall(indicator) ?
           (IdeaPluginDescriptorImpl)downloader.myDescriptor :
           null;
  }

  @RequiresBackgroundThread
  public boolean prepareToInstall(@NotNull ProgressIndicator indicator) throws IOException {
    myShownErrors = false;

    if (myFile != null) {
      IdeaPluginDescriptorImpl actualDescriptor = loadDescriptorFromArtifact();
      if (actualDescriptor == null) {
        reportError(IdeBundle.message("error.descriptor.load.failed", myFile.getPath()));
        return false;
      }

      myDescriptor = actualDescriptor;
      return true;
    }

    IdeaPluginDescriptor descriptor = null;
    if (!Boolean.getBoolean(StartupActionScriptManager.STARTUP_WIZARD_MODE) &&
        PluginManagerCore.isPluginInstalled(myPluginId)) {
      //store old plugins file
      descriptor = PluginManagerCore.getPlugin(myPluginId);
      LOG.assertTrue(descriptor != null);
      if (myPluginVersion != null && compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor) <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return false;
      }
      myOldFile = descriptor.isBundled() ? null : descriptor.getPluginPath();
    }

    try {
      myFile = tryDownloadPlugin(indicator);
    }
    catch (IOException e) {
      LOG.info(e);

      String message = e.getMessage();
      reportError(message != null ? message : IdeBundle.message("unknown.error"));
      return false;
    }

    boolean loaded = LoadingState.COMPONENTS_LOADED.isOccurred(); // plugins can be requested during initial IDE setup (e.g. in Rider initial setup wizard).
    if (loaded &&
        !PluginSignatureChecker.verifyIfRequired(myDescriptor, myFile, isFromMarketplace(), true)) {
      myShownErrors = true;
      return false;
    }

    IdeaPluginDescriptorImpl actualDescriptor = loadDescriptorFromArtifact();
    if (actualDescriptor == null) {
      reportError(IdeBundle.message("error.downloaded.descriptor.load.failed"));
      return false;
    }

    if (loaded &&
        InstalledPluginsState.getInstance().wasUpdated(actualDescriptor.getPluginId())) {
      reportError(IdeBundle.message("error.pending.update", getPluginName()));
      return false; //already updated
    }

    myPluginVersion = actualDescriptor.getVersion();
    if (descriptor != null && compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor) <= 0) {
      LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
      reportError(IdeBundle.message("error.older.update", myPluginVersion, descriptor.getVersion()));
      return false; //was not updated
    }

    myDescriptor = actualDescriptor;

    BuildNumber buildNumber = myBuildNumber != null ? myBuildNumber : PluginManagerCore.getBuildNumber();
    PluginLoadingError incompatibleError = PluginManagerCore.checkBuildNumberCompatibility(actualDescriptor, buildNumber);
    if (incompatibleError != null) {
      LOG.info("Plugin " + myPluginId + " is incompatible with current installation " +
               "(since:" + actualDescriptor.getSinceBuild() + " until:" + actualDescriptor.getUntilBuild() + ")");
      reportError(IdeBundle.message("error.incompatible.update", XmlStringUtil.escapeString(incompatibleError.getDetailedMessage())));
      return false; //host outdated plugins, no compatible plugin for new version
    }

    return true;
  }

  @RequiresBackgroundThread
  private @Nullable IdeaPluginDescriptorImpl loadDescriptorFromArtifact() throws IOException {
    return PluginDescriptorLoader.loadDescriptorFromArtifact(getFilePath(), myBuildNumber);
  }

  private void reportError(@NotNull @NlsContexts.NotificationContent String errorMessage) {
    LOG.info("PluginDownloader error: " + errorMessage);
    myShownErrors = true;
    myErrorsConsumer.accept(IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage));
  }

  public static void showErrorDialog(@NotNull @NlsContexts.NotificationContent String text) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return;
    }

    application.invokeLater(() -> {
      Messages.showErrorDialog(text,
                               IdeBundle.message("title.plugin.installation"));
    }, ModalityState.any());
  }

  public static String getMarketplaceDownloadsUUID() {
    try {
      return DeviceIdManager.getOrGenerateId(new DeviceIdManager.DeviceIdToken() { }, "MarketplaceDownloads");
    }
    catch (DeviceIdManager.InvalidDeviceIdTokenException e) {
      return "";
    }
  }

  public static int compareVersionsSkipBrokenAndIncompatible(String newPluginVersion,
                                                             @NotNull IdeaPluginDescriptor existingPlugin) {
    return compareVersionsSkipBrokenAndIncompatible(newPluginVersion, existingPlugin, null);
  }

  public static int compareVersionsSkipBrokenAndIncompatible(String newPluginVersion,
                                                             @NotNull IdeaPluginDescriptor existingPlugin,
                                                             @Nullable BuildNumber newBuildNumber) {
    int state = VersionComparatorUtil.compare(newPluginVersion, existingPlugin.getVersion());
    if (state < 0 &&
        (PluginManagerCore.isBrokenPlugin(existingPlugin) || PluginManagerCore.isIncompatible(existingPlugin, newBuildNumber))) {
      state = 1;
    }
    return state;
  }

  public void install() throws IOException {
    PluginInstaller.installAfterRestartAndKeepIfNecessary(myDescriptor, getFilePath(),
                                                          myOldFile
    );

    if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
      InstalledPluginsState.getInstance().onPluginInstall(myDescriptor,
                                                          PluginManagerCore.isPluginInstalled(myDescriptor.getPluginId()),
                                                          true);
    }
    else {
      InstalledPluginsState.addPreInstalledPlugin(myDescriptor);
    }
  }

  public boolean installDynamically(@Nullable JComponent ownerComponent) throws IOException {
    assert myDescriptor instanceof IdeaPluginDescriptorImpl;
    IdeaPluginDescriptorImpl descriptor = (IdeaPluginDescriptorImpl)myDescriptor;

    // yes, if no installed plugin by id, it means that something goes wrong, so do not try to install and load
    boolean appliedWithoutRestart = DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor) &&
                                    (myOldFile == null || unloadDescriptorById(myDescriptor.getPluginId())) &&
                                    PluginInstaller.installAndLoadDynamicPlugin(getFilePath(), ownerComponent, descriptor);

    if (!appliedWithoutRestart) {
      install();
    }

    return appliedWithoutRestart;
  }

  @RequiresBackgroundThread
  private @NotNull File tryDownloadPlugin(@NotNull ProgressIndicator indicator) throws IOException {
    indicator.checkCanceled();
    indicator.setText2(IdeBundle.message("progress.downloading.plugin", getPluginName()));

    MarketplacePluginDownloadService downloader = myDownloadService != null ? myDownloadService : new MarketplacePluginDownloadService();
    return myOldFile != null ?
           downloader.downloadPluginViaBlockMap(myPluginUrl, myOldFile, indicator) :
           downloader.downloadPlugin(myPluginUrl, indicator);
  }

  private static boolean unloadDescriptorById(@NotNull PluginId pluginId) {
    IdeaPluginDescriptorImpl descriptor = PluginManagerCore.findPlugin(pluginId);
    return descriptor != null &&
           DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor) &&
           DynamicPlugins.INSTANCE.unloadPlugin(descriptor,
                                                new DynamicPlugins.UnloadPluginOptions()
                                                  .withDisable(false)
                                                  .withUpdate(true)
                                                  .withWaitForClassloaderUnload(true));
  }

  // creators-converters
  public static @NotNull PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor) throws IOException {
    return createDownloader(descriptor, null, null);
  }

  public static @NotNull PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor,
                                                           @Nullable String host,
                                                           @Nullable BuildNumber buildNumber) throws IOException {
    String url = descriptor instanceof PluginNode && host != null ?
                 toAbsoluteUrl(((PluginNode)descriptor).getDownloadUrl(), host) :
                 getMarketplaceBuildUrl(descriptor, buildNumber);

    return new PluginDownloader(descriptor,
                                url,
                                buildNumber,
                                PluginDownloader::showErrorDialog,
                                null);
  }

  public @NotNull PluginNode toPluginNode() {
    return toPluginNode(null);
  }

  public @NotNull PluginNode toPluginNode(@Nullable String host) {
    IdeaPluginDescriptor descriptor = getDescriptor();
    if (descriptor instanceof PluginNode) {
      return (PluginNode)descriptor;
    }

    PluginNode node = new PluginNode(getId());
    node.setName(getPluginName());
    node.setProductCode(getProductCode());
    node.setReleaseDate(getReleaseDate());
    node.setReleaseVersion(getReleaseVersion());
    node.setLicenseOptional(isLicenseOptional());
    node.setVersion(getPluginVersion());
    node.setRepositoryName(host);
    node.setDownloadUrl(myPluginUrl);
    node.setDependencies(myDependencies);
    node.setDescription(myDescription);
    return node;
  }

  private static @NotNull String toAbsoluteUrl(@NotNull String downloadUrl,
                                               @NotNull String host) throws IOException {
    try {
      return new URI(downloadUrl).isAbsolute() ?
             downloadUrl :
             new URL(new URL(host), downloadUrl).toExternalForm();
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private static @NotNull String getMarketplaceBuildUrl(@NotNull IdeaPluginDescriptor descriptor,
                                                        @Nullable BuildNumber buildNumber) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("id", descriptor.getPluginId().getIdString());
    parameters.put("build", ApplicationInfoImpl.orFromPluginsCompatibleBuild(buildNumber));
    parameters.put("uuid", getMarketplaceDownloadsUUID());

    if (descriptor instanceof PluginNode) {
      String channel = ((PluginNode)descriptor).getChannel();
      if (channel != null) {
        parameters.put("channel", channel);
      }
    }

    return Urls.newFromEncoded(ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl())
      .addParameters(Collections.unmodifiableMap(parameters))
      .toExternalForm();
  }

  @ApiStatus.Internal
  public static void runSynchronouslyInBackground(@NotNull Runnable runnable) {
    try {
      Thread thread = new Thread(runnable, "Plugin downloader");
      thread.start();
      thread.join();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
