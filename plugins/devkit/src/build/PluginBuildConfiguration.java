/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileContainer;
import com.intellij.util.descriptors.ConfigFileFactory;
import com.intellij.util.descriptors.ConfigFileInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginDescriptorConstants;

import java.io.File;

public class PluginBuildConfiguration implements ModuleComponent, JDOMExternalizable {
  private final Module myModule;
  private final ConfigFileContainer myPluginXmlContainer;
  private VirtualFilePointer myManifestFilePointer;
  private boolean myUseUserManifest = false;
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String MANIFEST_ATTR = "manifest";
  @NonNls private static final String META_INF = "META-INF";
  @NonNls private static final String PLUGIN_XML = "plugin.xml";
  private final PluginBuildParticipant myBuildParticipant;

  public PluginBuildConfiguration(Module module) {
    myModule = module;
    myPluginXmlContainer = ConfigFileFactory.getInstance().createSingleFileContainer(myModule.getProject(), PluginDescriptorConstants.META_DATA);
    Disposer.register(module, myPluginXmlContainer);
    myBuildParticipant = new PluginBuildParticipant(module, this);
  }

  @Nullable
  public static PluginBuildConfiguration getInstance(Module module) {
    return module.getComponent(PluginBuildConfiguration.class);
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public void moduleAdded() {}

  @NotNull
  public String getComponentName() {
    return "DevKit.ModuleBuildProperties";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
                                                                       
  public void readExternal(Element element) throws InvalidDataException {
    String url = element.getAttributeValue(URL_ATTR);
    if (url != null) {
      myPluginXmlContainer.getConfiguration().replaceConfigFile(PluginDescriptorConstants.META_DATA, url);
    }
    url = element.getAttributeValue(MANIFEST_ATTR);
    if (url != null) {
      setManifestPath(VfsUtil.urlToPath(url));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(URL_ATTR, getPluginXmlUrl());
    if (myManifestFilePointer != null){
      element.setAttribute(MANIFEST_ATTR, myManifestFilePointer.getUrl());
    }
  }

  @Nullable
  public ConfigFile getPluginXML() {
    return myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  private void createDescriptor(final String url) {
    final ConfigFileInfo descriptor = new ConfigFileInfo(PluginDescriptorConstants.META_DATA, url);
    myPluginXmlContainer.getConfiguration().addConfigFile(descriptor);
    ConfigFileFactory.getInstance().createFile(myModule.getProject(), descriptor.getUrl(), PluginDescriptorConstants.META_DATA.getDefaultVersion(),
                                               false);
  }

  @Nullable
  public ConfigFile getPluginXmlConfigFile() {
    return myPluginXmlContainer.getConfigFile(PluginDescriptorConstants.META_DATA);
  }

  @Nullable
  private String getPluginXmlUrl() {
    ConfigFile configFile = getPluginXmlConfigFile();
    return configFile != null ? configFile.getUrl() : null;
  }

  private String getDefaultLocation() {
    return new File(myModule.getModuleFilePath()).getParent() + File.separator + META_INF + File.separator + PLUGIN_XML;
  }

  @NotNull
  public String getPluginXmlPath() {
    String url = getPluginXmlUrl();
    if (url == null) {
      return getDefaultLocation();
    }
    return FileUtil.toSystemDependentName(VfsUtil.urlToPath(url));
  }

  public void setPluginXmlPathAndCreateDescriptorIfDoesntExist(final String pluginXmlPath) {
    myPluginXmlContainer.getConfiguration().removeConfigFiles(PluginDescriptorConstants.META_DATA);
    new WriteAction() {
      protected void run(final Result result) throws Throwable {
        createDescriptor(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(pluginXmlPath)));
      }
    }.execute();
  }

  public void setManifestPath(final String manifestPath) {
    if (manifestPath == null || manifestPath.length() == 0){
      myManifestFilePointer = null;
    } else {

      final VirtualFile manifest = LocalFileSystem.getInstance().findFileByPath(manifestPath);
      if (manifest == null){
        Messages.showErrorDialog(myModule.getProject(), DevKitBundle.message("error.file.not.found.message", manifestPath), DevKitBundle.message("error.file.not.found"));
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            myManifestFilePointer = VirtualFilePointerManager.getInstance().create(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(manifestPath)), myModule, null);
          }
        });
      } else {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            myManifestFilePointer = VirtualFilePointerManager.getInstance().create(manifest, myModule, null);
          }
        });
      }
    }
  }

  @Nullable
  public String getManifestPath() {
    if (myManifestFilePointer != null){
      return FileUtil.toSystemDependentName(myManifestFilePointer.getPresentableUrl());
    }
    return null;
  }

  @Nullable
  public VirtualFile getManifest(){
    if (myManifestFilePointer != null){
      return myManifestFilePointer.getFile();
    }
    return null;
  }

  public boolean isUseUserManifest() {
    return myUseUserManifest;
  }

  public void setUseUserManifest(final boolean useUserManifest) {
    myUseUserManifest = useUserManifest;
  }

  public PluginBuildParticipant getBuildParticipant() {
    return myBuildParticipant;
  }
}
