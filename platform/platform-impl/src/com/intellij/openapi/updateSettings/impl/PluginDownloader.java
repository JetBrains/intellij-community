// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableNotNullBiFunction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Urls;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

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

  private @NlsSafe String myPluginVersion;
  private IdeaPluginDescriptor myDescriptor;
  private File myFile;
  private Path myOldFile;
  private MarketplacePluginDownloadService myDownloadService;

  private boolean myShownErrors;
  private @Nullable List<String> myErrors;

  private PluginDownloader(@NotNull IdeaPluginDescriptor descriptor,
                           @NotNull String url,
                           @Nullable BuildNumber buildNumber) {
    myPluginId = descriptor.getPluginId();
    myPluginName = descriptor.getName();
    myProductCode = descriptor.getProductCode();
    myReleaseDate = descriptor.getReleaseDate();
    myReleaseVersion = descriptor.getReleaseVersion();
    myLicenseOptional = descriptor.isLicenseOptional();
    myDescription = descriptor.getDescription();
    myDependencies = descriptor.getDependencies();

    myPluginUrl = url;
    myBuildNumber = buildNumber;

    myPluginVersion = descriptor.getVersion();
    myDescriptor = descriptor;
  }

  public void setDownloadFunction(@NotNull ThrowableNotNullBiFunction<? super String, ? super ProgressIndicator, ? extends File, ? extends IOException> downloadFunction) {
    myDownloadService = new MarketplacePluginDownloadService() {
      @Override
      public @NotNull File downloadPlugin(@NotNull String pluginUrl, @NotNull ProgressIndicator indicator) throws IOException {
        return downloadFunction.fun(pluginUrl, indicator);
      }
    };
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

  public File getFile() {
    return myFile;
  }

  public boolean isShownErrors() {
    return myShownErrors;
  }

  public void setErrorsCollector(@Nullable List<String> errors) {
    myErrors = errors;
  }

  public boolean prepareToInstall(@NotNull ProgressIndicator indicator) throws IOException {
    return prepareToInstallAndLoadDescriptor(indicator) != null;
  }

  public @Nullable IdeaPluginDescriptorImpl prepareToInstallAndLoadDescriptor(@NotNull ProgressIndicator indicator) throws IOException {
    return prepareToInstallAndLoadDescriptor(indicator, true);
  }

  public @Nullable IdeaPluginDescriptorImpl prepareToInstallAndLoadDescriptor(@NotNull ProgressIndicator indicator,
                                                                              boolean showMessageOnError) throws IOException {
    myShownErrors = false;

    if (myFile != null) {
      IdeaPluginDescriptorImpl actualDescriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(myFile.toPath(), myBuildNumber);
      if (actualDescriptor == null) {
        reportError(showMessageOnError, IdeBundle.message("error.descriptor.load.failed", myFile.getPath()));
      }
      myDescriptor = actualDescriptor;
      return actualDescriptor;
    }

    IdeaPluginDescriptor descriptor = null;
    if (!Boolean.getBoolean(StartupActionScriptManager.STARTUP_WIZARD_MODE) &&
        PluginManagerCore.isPluginInstalled(myPluginId)) {
      //store old plugins file
      descriptor = PluginManagerCore.getPlugin(myPluginId);
      LOG.assertTrue(descriptor != null);
      if (myPluginVersion != null && compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor) <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return null;
      }
      myOldFile = descriptor.isBundled() ? null : descriptor.getPluginPath();
    }

    myFile = tryDownloadPlugin(indicator, showMessageOnError);
    if (myFile == null) return null;

    // The null check is required for cases when plugins are requested during initial IDE setup (e.g. in Rider initial setup wizard).
    if (requiresSignatureCheck()) {
      boolean certified = PluginSignatureChecker.verify(myDescriptor, myFile, showMessageOnError);
      if (!certified) {
        myShownErrors = true;
        return null;
      }
    }

    IdeaPluginDescriptorImpl actualDescriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(myFile.toPath(), myBuildNumber);
    if (actualDescriptor != null) {
      InstalledPluginsState state = InstalledPluginsState.getInstanceIfLoaded();
      if (state != null && state.wasUpdated(actualDescriptor.getPluginId())) {
        reportError(showMessageOnError, IdeBundle.message("error.pending.update", getPluginName()));
        return null; //already updated
      }

      myPluginVersion = actualDescriptor.getVersion();
      if (descriptor != null && compareVersionsSkipBrokenAndIncompatible(myPluginVersion, descriptor) <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        reportError(showMessageOnError, IdeBundle.message("error.older.update", myPluginVersion, descriptor.getVersion()));
        return null; //was not updated
      }

      myDescriptor = actualDescriptor;

      PluginLoadingError incompatibleError =
        PluginManagerCore.checkBuildNumberCompatibility(actualDescriptor, myBuildNumber != null ? myBuildNumber : PluginManagerCore.getBuildNumber());
      if (incompatibleError != null) {
        LOG.info("Plugin " + myPluginId + " is incompatible with current installation " +
                 "(since:" + actualDescriptor.getSinceBuild() + " until:" + actualDescriptor.getUntilBuild() + ")");
        reportError(showMessageOnError, IdeBundle.message("error.incompatible.update", XmlStringUtil.escapeString(incompatibleError.getDetailedMessage())));
        return null; //host outdated plugins, no compatible plugin for new version
      }
    }
    else {
      reportError(showMessageOnError, IdeBundle.message("error.downloaded.descriptor.load.failed"));
    }

    return actualDescriptor;
  }

  private boolean requiresSignatureCheck() {
    if (ApplicationManager.getApplication() == null) {
      return false;
    }
    if (isFromMarketplace()) {
      return Registry.is("marketplace.certificate.signature.check");
    }
    else {
      return Registry.is("custom-repository.certificate.signature.check");
    }
  }

  private boolean isPluginFromBuiltinRepo() {
    String builtinPluginsUrlPluginsXml = ApplicationInfoImpl.getShadowInstance().getBuiltinPluginsUrl();
    String builtinPluginsUrl = null;
    if (builtinPluginsUrlPluginsXml != null) {
      builtinPluginsUrl = StringUtil.substringBeforeLast(builtinPluginsUrlPluginsXml, "/");
    }
    if (builtinPluginsUrl != null) {
      try {
        URL builtinPluginsUrlURL = new URL(builtinPluginsUrl);
        URL myPluginUrlURL = new URL(myPluginUrl);
        if (!myPluginUrlURL.getHost().equals(builtinPluginsUrlURL.getHost())) return false;
        if (!myPluginUrlURL.getPath().startsWith(builtinPluginsUrlURL.getPath())) return false;
        return true;
      } catch (MalformedURLException ignored) {
        return false;
      }
    }
    return false;
  }

  private void reportError(boolean showMessageOnError, @Nullable @Nls String errorMessage) {
    LOG.info("PluginDownloader error: " + errorMessage);
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      myShownErrors = true;
      if (showMessageOnError || myErrors != null) {
        if (errorMessage == null) {
          errorMessage = IdeBundle.message("unknown.error");
        }
        String text = IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage);
        if (myErrors != null) {
          myErrors.add(text);
        }
        else if (showMessageOnError) {
          String title = IdeBundle.message("title.plugin.installation");
          app.invokeLater(() -> Messages.showErrorDialog(text, title), ModalityState.any());
        }
      }
    }
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
    if (myFile == null) {
      throw new IOException("Plugin '" + getPluginName() + "' was not successfully downloaded");
    }

    boolean deletePluginSource = !Registry.is("ide.plugins.keep.archive", true);
    PluginInstaller.installAfterRestart(myFile.toPath(), deletePluginSource, myOldFile, myDescriptor);

    InstalledPluginsState state = InstalledPluginsState.getInstanceIfLoaded();
    if (state != null) {
      state.onPluginInstall(myDescriptor, PluginManagerCore.isPluginInstalled(myDescriptor.getPluginId()), true);
    }
    else {
      InstalledPluginsState.addPreInstalledPlugin(myDescriptor);
    }
  }

  public boolean tryInstallWithoutRestart(@Nullable JComponent ownerComponent) {
    assert myDescriptor instanceof IdeaPluginDescriptorImpl;
    IdeaPluginDescriptorImpl descriptor = (IdeaPluginDescriptorImpl)myDescriptor;
    if (!DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor)) {
      return false;
    }

    if (myOldFile != null) {
      IdeaPluginDescriptorImpl installedPlugin = (IdeaPluginDescriptorImpl)PluginManagerCore.getPlugin(myDescriptor.getPluginId());
      // yes, if no installed plugin by id, it means that something goes wrong, so do not try to install and load
      if (installedPlugin == null || !DynamicPlugins.INSTANCE.unloadPlugin(descriptor,
                                                                           new DynamicPlugins.UnloadPluginOptions()
                                                                             .withDisable(false)
                                                                             .withUpdate(true)
                                                                             .withWaitForClassloaderUnload(true))) {
        return false;
      }
    }

    return PluginInstaller.installAndLoadDynamicPlugin(myFile.toPath(), ownerComponent, descriptor);
  }

  private @Nullable File tryDownloadPlugin(@NotNull ProgressIndicator indicator, boolean showMessageOnError) {
    indicator.checkCanceled();
    indicator.setText2(IdeBundle.message("progress.downloading.plugin", getPluginName()));

    MarketplacePluginDownloadService downloader = myDownloadService != null ? myDownloadService : MarketplacePluginDownloadService.getInstance();
    try {
      return myOldFile != null ?
             downloader.downloadPluginViaBlockMap(myPluginUrl, myOldFile, indicator) :
             downloader.downloadPlugin(myPluginUrl, indicator);
    }
    catch (IOException ex) {
      LOG.warn(ex);
      reportError(showMessageOnError, ex.getMessage());
      return null;
    }
  }

  // creators-converters
  public static @NotNull PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor) throws IOException {
    return createDownloader(descriptor, null, null);
  }

  public static @NotNull PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor,
                                                           @Nullable String host,
                                                           @Nullable BuildNumber buildNumber) throws IOException {
    String url = descriptor instanceof PluginNode && host != null ?
                 getDownloadUrl((PluginNode)descriptor, host) :
                 getUrl(descriptor.getPluginId(), buildNumber);
    return new PluginDownloader(descriptor,
                                url,
                                buildNumber);
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

  private static String getDownloadUrl(PluginNode pluginNode, String host) throws IOException {
    String url = pluginNode.getDownloadUrl();
    try {
      return new URI(url).isAbsolute() ? url : new URL(new URL(host), url).toExternalForm();
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private static String getUrl(PluginId pluginId, @Nullable BuildNumber buildNumber) {
    return Urls.newFromEncoded(ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl())
      .addParameters(Map.of("id", pluginId.getIdString(),
                            "build", ApplicationInfoImpl.orFromPluginsCompatibleBuild(buildNumber),
                            "uuid", getMarketplaceDownloadsUUID()))
      .toExternalForm();
  }
}
