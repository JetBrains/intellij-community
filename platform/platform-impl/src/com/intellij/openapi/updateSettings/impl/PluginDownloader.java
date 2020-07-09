// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.Urls;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anna
 */
public final class PluginDownloader {
  private static final Logger LOG = Logger.getInstance(PluginDownloader.class);

  private final PluginId myPluginId;
  private final String myPluginName;
  private final @Nullable String myProductCode;
  private final Date myReleaseDate;
  private final int myReleaseVersion;
  private final boolean myLicenseOptional;
  private final String myDescription;
  private final @NotNull List<IdeaPluginDependency> myDependencies;

  private final String myPluginUrl;
  private final BuildNumber myBuildNumber;

  private String myPluginVersion;
  private IdeaPluginDescriptor myDescriptor;
  private File myFile;
  private Path myOldFile;
  private MarketplaceRequests myMarketplaceRequests = MarketplaceRequests.getInstance();

  private boolean myShownErrors;

  private PluginDownloader(@NotNull IdeaPluginDescriptor descriptor, @NotNull String url, @Nullable BuildNumber buildNumber) {
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

  public void setMarketplaceRequests(MarketplaceRequests requests) {
    myMarketplaceRequests = requests;
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

  public String getPluginVersion() {
    return myPluginVersion;
  }

  public @NotNull String getPluginName() {
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

  public boolean prepareToInstall(@NotNull ProgressIndicator indicator) throws IOException {
    return prepareToInstallAndLoadDescriptor(indicator) != null;
  }

  public @Nullable IdeaPluginDescriptorImpl prepareToInstallAndLoadDescriptor(@NotNull ProgressIndicator indicator) throws IOException {
    return prepareToInstallAndLoadDescriptor(indicator, true);
  }

  @Nullable
  public IdeaPluginDescriptorImpl prepareToInstallAndLoadDescriptor(@NotNull ProgressIndicator indicator, boolean showMessageOnError) throws IOException {
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

    // download plugin
    String errorMessage = null;
    try {
      myFile = downloadPlugin(indicator);
    }
    catch (IOException ex) {
      myFile = null;
      LOG.warn(ex);
      errorMessage = ex.getMessage();
    }
    if (myFile == null) {
      reportError(showMessageOnError, errorMessage);
      return null;
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

      if (PluginManagerCore.isIncompatible(actualDescriptor, myBuildNumber)) {
        LOG.info("Plugin " + myPluginId + " is incompatible with current installation " +
                 "(since:" + actualDescriptor.getSinceBuild() + " until:" + actualDescriptor.getUntilBuild() + ")");
        String incompatibleMessage =
          PluginManagerCore.getIncompatibleMessage(myBuildNumber != null ? myBuildNumber : PluginManagerCore.getBuildNumber(),
                                                   actualDescriptor.getSinceBuild(),
                                                   actualDescriptor.getUntilBuild());
        reportError(showMessageOnError, IdeBundle.message("error.incompatible.update", XmlStringUtil.escapeString(incompatibleMessage)));
        return null; //host outdated plugins, no compatible plugin for new version
      }
    }
    else {
      reportError(showMessageOnError, IdeBundle.message("error.downloaded.descriptor.load.failed"));
    }

    return actualDescriptor;
  }

  private void reportError(boolean showMessageOnError, @Nullable @Nls String errorMessage) {
    LOG.info("PluginDownloader error: " + errorMessage);
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      myShownErrors = true;
      if (showMessageOnError) {
        if (errorMessage == null) {
          errorMessage = IdeBundle.message("unknown.error");
        }
        String text = IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage);
        String title = IdeBundle.message("title.plugin.installation");
        app.invokeLater(() -> Messages.showErrorDialog(text, title), ModalityState.any());
      }
    }
  }

  public static int compareVersionsSkipBrokenAndIncompatible(String newPluginVersion,
                                                             @NotNull IdeaPluginDescriptor existingPlugin) {
    int state = VersionComparatorUtil.compare(newPluginVersion, existingPlugin.getVersion());
    if (state < 0 && (PluginManagerCore.isBrokenPlugin(existingPlugin) || PluginManagerCore.isIncompatible(existingPlugin))) {
      state = 1;
    }
    return state;
  }

  public void install() throws IOException {
    if (myFile == null) {
      throw new IOException("Plugin '" + getPluginName() + "' was not successfully downloaded");
    }

    PluginInstaller.installAfterRestart(myFile, true, myOldFile, myDescriptor);

    InstalledPluginsState state = InstalledPluginsState.getInstanceIfLoaded();
    if (state != null) {
      state.onPluginInstall(myDescriptor, PluginManagerCore.isPluginInstalled(myDescriptor.getPluginId()), true);
    }
    else {
      InstalledPluginsState.addPreInstalledPlugin(myDescriptor);
    }
  }

  public boolean tryInstallWithoutRestart(@Nullable JComponent ownerComponent) {
    final IdeaPluginDescriptorImpl descriptorImpl = (IdeaPluginDescriptorImpl)myDescriptor;
    if (!DynamicPlugins.allowLoadUnloadWithoutRestart(descriptorImpl)) return false;

    if (myOldFile != null) {
      IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(myDescriptor.getPluginId());
      if (installedPlugin == null) {
        return false;
      }
      IdeaPluginDescriptorImpl installedPluginDescriptor = PluginDescriptorLoader.tryLoadFullDescriptor((IdeaPluginDescriptorImpl)installedPlugin);
      if (installedPluginDescriptor == null || !DynamicPlugins.unloadPlugin(installedPluginDescriptor,
                                                                            new DynamicPlugins.UnloadPluginOptions().withUpdate(true).withWaitForClassloaderUnload(true))) {
        return false;
      }
    }

    return PluginInstaller.installAndLoadDynamicPlugin(myFile, ownerComponent, descriptorImpl);
  }

  private @NotNull File downloadPlugin(@NotNull ProgressIndicator indicator) throws IOException {
    indicator.checkCanceled();
    indicator.setText2(IdeBundle.message("progress.downloading.plugin", getPluginName()));

    return myMarketplaceRequests.download(myPluginUrl, indicator);
  }

  // creators-converters
  public static PluginDownloader createDownloader(@NotNull IdeaPluginDescriptor descriptor) throws IOException {
    return createDownloader(descriptor, null, null);
  }

  public static @NotNull PluginDownloader createDownloader(
    @NotNull IdeaPluginDescriptor descriptor,
    @Nullable String host,
    @Nullable BuildNumber buildNumber
  ) throws IOException {
    String url;
    try {
      if (host != null && descriptor instanceof PluginNode) {
        url = ((PluginNode)descriptor).getDownloadUrl();
        if (!new URI(url).isAbsolute()) {
          url = new URL(new URL(host), url).toExternalForm();
        }
      }
      else {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("id", descriptor.getPluginId().getIdString());
        parameters.put("build", getBuildNumberForDownload(buildNumber));
        parameters.put("uuid", PermanentInstallationID.get());
        url = Urls
          .newFromEncoded(ApplicationInfoImpl.getShadowInstance().getPluginsDownloadUrl())
          .addParameters(parameters)
          .toExternalForm();
      }
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
    return new PluginDownloader(descriptor, url, buildNumber);
  }

  public static @NotNull String getBuildNumberForDownload(@Nullable BuildNumber buildNumber) {
    return buildNumber != null ? buildNumber.asString() : MarketplaceRequests.getInstance().getBuildForPluginRepositoryRequests();
  }

  public static @NotNull PluginNode createPluginNode(@Nullable String host, @NotNull PluginDownloader downloader) {
    IdeaPluginDescriptor descriptor = downloader.getDescriptor();
    if (descriptor instanceof PluginNode) {
      return (PluginNode)descriptor;
    }

    PluginNode node = new PluginNode(downloader.myPluginId);
    node.setName(downloader.getPluginName());
    node.setProductCode(downloader.getProductCode());
    node.setReleaseDate(downloader.getReleaseDate());
    node.setReleaseVersion(downloader.getReleaseVersion());
    node.setLicenseOptional(downloader.isLicenseOptional());
    node.setVersion(downloader.getPluginVersion());
    node.setRepositoryName(host);
    node.setDownloadUrl(downloader.myPluginUrl);
    node.setDependencies(downloader.myDependencies);
    node.setDescription(downloader.myDescription);
    return node;
  }
}