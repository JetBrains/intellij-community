// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
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
  private final Module module;
  private final ConfigFileContainer pluginXmlContainer;
  private VirtualFilePointer manifestFilePointer;
  private boolean useUserManifest = false;
  private static final @NonNls String META_INF = "META-INF";
  private static final @NonNls String PLUGIN_XML = "plugin.xml";

  private State state = new State();

  public PluginBuildConfiguration(@NotNull Module module) {
    this.module = module;
    pluginXmlContainer = ConfigFileFactory.getInstance().createSingleFileContainer(this.module.getProject(), PluginDescriptorConstants.META_DATA);
    Disposer.register(module, pluginXmlContainer);
  }

  public static @Nullable PluginBuildConfiguration getInstance(@NotNull Module module) {
    return ModuleType.is(module, PluginModuleType.getInstance()) ? module.getService(PluginBuildConfiguration.class) : null;
  }

  public static final class State {
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
    state.manifest = manifestFilePointer == null ? null : manifestFilePointer.getUrl();
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
    if (state.url != null) {
      pluginXmlContainer.getConfiguration().replaceConfigFile(PluginDescriptorConstants.META_DATA, state.url);
    }
    if (state.manifest != null) {
      setManifestPath(VfsUtilCore.urlToPath(state.manifest));
    }
  }

  @TestOnly
  public void setPluginXmlFromVirtualFile(VirtualFile virtualFile) {
    pluginXmlContainer.getConfiguration().replaceConfigFile(PluginDescriptorConstants.META_DATA, virtualFile.getUrl());
  }

  @TestOnly
  public void cleanupForNextTest() {
    pluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
  }

  private void createDescriptor(final String url) {
    ConfigFileInfo descriptor = new ConfigFileInfo(PluginDescriptorConstants.META_DATA, url);
    pluginXmlContainer.getConfiguration().addConfigFile(descriptor);
    ConfigFileFactory.getInstance().createFile(module.getProject(), descriptor.getUrl(), PluginDescriptorConstants.META_DATA.getDefaultVersion(),
                                               false);
  }

  public @Nullable ConfigFile getPluginXmlConfigFile() {
    return pluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  private @Nullable String getPluginXmlUrl() {
    ConfigFile configFile = getPluginXmlConfigFile();
    return configFile != null ? configFile.getUrl() : null;
  }

  private String getDefaultLocation() {
    return module.getModuleNioFile().getParent().toString() + File.separator + META_INF + File.separator + PLUGIN_XML;
  }

  public @NotNull @NlsSafe String getPluginXmlPath() {
    String url = getPluginXmlUrl();
    if (url == null) {
      return getDefaultLocation();
    }
    return FileUtilRt.toSystemDependentName(VfsUtilCore.urlToPath(url));
  }

  public void setPluginXmlPathAndCreateDescriptorIfDoesntExist(final String pluginXmlPath) {
    pluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
    WriteAction.runAndWait(() -> createDescriptor(VfsUtilCore.pathToUrl(pluginXmlPath)));
  }

  public @Nullable @NlsSafe String getManifestPath() {
    return manifestFilePointer != null ? FileUtilRt.toSystemDependentName(manifestFilePointer.getPresentableUrl()) : null;
  }

  public void setManifestPath(@Nullable String manifestPath) {
    if (Strings.isEmpty(manifestPath)) {
      manifestFilePointer = null;
      return;
    }

    VirtualFile manifest = LocalFileSystem.getInstance().findFileByPath(manifestPath);
    VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
    if (manifest == null) {
      Messages.showErrorDialog(module.getProject(), DevKitBundle.message("error.file.not.found.message", manifestPath), DevKitBundle.message("error.file.not.found"));
      ReadAction.run(() -> {
        manifestFilePointer = virtualFilePointerManager.create(VfsUtilCore.pathToUrl(manifestPath), module, null);
      });
    }
    else {
      ReadAction.run(() -> manifestFilePointer = virtualFilePointerManager.create(manifest, module, null));
    }
  }

  public @Nullable VirtualFile getManifest(){
    return manifestFilePointer != null ? manifestFilePointer.getFile() : null;
  }

  public boolean isUseUserManifest() {
    return useUserManifest;
  }

  public void setUseUserManifest(final boolean useUserManifest) {
    this.useUserManifest = useUserManifest;
  }
}
