// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileContainer;
import com.intellij.util.descriptors.ConfigFileFactory;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginDescriptorConstants;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.File;
import java.util.Objects;

@State(name = "DevKit.ModuleBuildProperties")
public final class PluginBuildConfiguration implements PersistentStateComponent<PluginBuildConfiguration.State> {
  private final Module myModule;
  private final ConfigFileContainer myPluginXmlContainer;
  private VirtualFilePointer myManifestFilePointer;
  private boolean myUseUserManifest = false;
  private static final @NonNls String META_INF = "META-INF";
  private static final @NonNls String PLUGIN_XML = "plugin.xml";

  private State state = new State();

  public PluginBuildConfiguration(@NotNull Module module) {
    myModule = module;
    myPluginXmlContainer = ConfigFileFactory.getInstance().createSingleFileContainer(myModule.getProject(), PluginDescriptorConstants.META_DATA);
    Disposer.register(module, myPluginXmlContainer);
  }

  public static @Nullable PluginBuildConfiguration getInstance(@NotNull Module module) {
    return ModuleType.is(module, PluginModuleType.getInstance()) ? module.getService(PluginBuildConfiguration.class) : null;
  }

  public static class State {
    @Attribute
    String url;

    @Attribute
    String manifest;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;

      if (!Objects.equals(url, state.url)) return false;
      if (!Objects.equals(manifest, state.manifest)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = url != null ? url.hashCode() : 0;
      result = 31 * result + (manifest != null ? manifest.hashCode() : 0);
      return result;
    }
  }

  @Override
  public @Nullable State getState() {
    state.url = getPluginXmlUrl();
    state.manifest = myManifestFilePointer == null ? null : myManifestFilePointer.getUrl();
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
    if (state.url != null) {
      myPluginXmlContainer.getConfiguration().replaceConfigFile(PluginDescriptorConstants.META_DATA, state.url);
    }
    if (state.manifest != null) {
      setManifestPath(VfsUtilCore.urlToPath(state.manifest));
    }
  }

  public @Nullable ConfigFile getPluginXML() {
    return myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  @TestOnly
  public void setPluginXmlFromVirtualFile(VirtualFile virtualFile) {
    myPluginXmlContainer.getConfiguration().replaceConfigFile(PluginDescriptorConstants.META_DATA, virtualFile.getUrl());
  }

  @TestOnly
  public void cleanupForNextTest() {
    myPluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
  }

  private void createDescriptor(final String url) {
    final ConfigFileInfo descriptor = new ConfigFileInfo(PluginDescriptorConstants.META_DATA, url);
    myPluginXmlContainer.getConfiguration().addConfigFile(descriptor);
    ConfigFileFactory.getInstance().createFile(myModule.getProject(), descriptor.getUrl(), PluginDescriptorConstants.META_DATA.getDefaultVersion(),
                                               false);
  }

  public @Nullable ConfigFile getPluginXmlConfigFile() {
    return myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  private @Nullable String getPluginXmlUrl() {
    ConfigFile configFile = getPluginXmlConfigFile();
    return configFile != null ? configFile.getUrl() : null;
  }

  private String getDefaultLocation() {
    return new File(myModule.getModuleFilePath()).getParent() + File.separator + META_INF + File.separator + PLUGIN_XML;
  }

  public @NotNull @NlsSafe String getPluginXmlPath() {
    String url = getPluginXmlUrl();
    if (url == null) {
      return getDefaultLocation();
    }
    return FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(url));
  }

  public void setPluginXmlPathAndCreateDescriptorIfDoesntExist(final String pluginXmlPath) {
    myPluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
    WriteAction.runAndWait(() -> createDescriptor(VfsUtilCore.pathToUrl(pluginXmlPath)));
  }

  public void setManifestPath(@Nullable String manifestPath) {
    if (StringUtil.isEmpty(manifestPath)) {
      myManifestFilePointer = null;
      return;
    }

    VirtualFile manifest = LocalFileSystem.getInstance().findFileByPath(manifestPath);
    if (manifest == null) {
      Messages.showErrorDialog(myModule.getProject(), DevKitBundle.message("error.file.not.found.message", manifestPath), DevKitBundle.message("error.file.not.found"));
      ReadAction.run(()-> myManifestFilePointer = VirtualFilePointerManager.getInstance().create(
        VfsUtilCore.pathToUrl(manifestPath), myModule, null));
    }
    else {
      ReadAction.run(()-> myManifestFilePointer = VirtualFilePointerManager.getInstance().create(manifest, myModule, null));
    }
  }

  public @Nullable @NlsSafe String getManifestPath() {
    return myManifestFilePointer != null ? FileUtil.toSystemDependentName(myManifestFilePointer.getPresentableUrl()) : null;
  }

  public @Nullable VirtualFile getManifest(){
    return myManifestFilePointer != null ? myManifestFilePointer.getFile() : null;
  }

  public boolean isUseUserManifest() {
    return myUseUserManifest;
  }

  public void setUseUserManifest(final boolean useUserManifest) {
    myUseUserManifest = useUserManifest;
  }
}
