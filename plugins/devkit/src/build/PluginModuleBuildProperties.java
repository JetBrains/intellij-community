/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.j2ee.j2eeDom.DeploymentDescriptorFactory;
import com.intellij.j2ee.j2eeDom.J2EEDeploymentItem;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.idea.devkit.module.PluginDescriptorMetaData;

import java.io.File;

public class PluginModuleBuildProperties extends ModuleBuildProperties implements ModuleComponent, JDOMExternalizable {
  private Module myModule;
  private J2EEDeploymentItem myPluginXML;
  private VirtualFilePointer myVirtualFilePointer;
  public PluginModuleBuildProperties(Module module) {
    myModule = module;
  }

  public String getArchiveExtension() {
    return "jar";
  }

  public String getJarPath() {
    return null;
  }

  public String getExplodedPath() {
    return PluginBuildUtil.getPluginExPath(myModule);
  }

  public Module getModule() {
    return myModule;
  }

  public boolean isJarEnabled() {
    return false;
  }

  public boolean isExplodedEnabled() {
    return true;
  }

  public boolean isBuildOnFrameDeactivation() {
    return false;
  }

  public boolean isSyncExplodedDir() {
    return true;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public void moduleAdded() {}

  public String getComponentName() {
    return "DevKit.ModuleBuildProperties";
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    String url = element.getAttributeValue("url");
    if (url != null) {
      setPluginXMLUrl(VfsUtil.urlToPath(url));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute("url", getVirtualFilePointer().getUrl());
  }

  public J2EEDeploymentItem getPluginXML() {
    if (myPluginXML == null) {
      myPluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(myModule, new PluginDescriptorMetaData());
      myPluginXML.setUrl(getVirtualFilePointer().getUrl());
      myPluginXML.createIfNotExists();
    }
    return myPluginXML;
  }

  public VirtualFilePointer getVirtualFilePointer() {
    if (myVirtualFilePointer == null) {
      final String defaultPluginXMLLocation = new File(myModule.getModuleFilePath()).getParent() + File.separator + "META-INF" + File.separator + "plugin.xml";
      setPluginXMLUrl(defaultPluginXMLLocation);
    }
    return myVirtualFilePointer;
  }

  public String getPluginXmlPath() {
    return FileUtil.toSystemDependentName(getVirtualFilePointer().getFile().getPath());
  }

  public void setPluginXMLUrl(final String pluginXMLUrl) {
    myPluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(myModule, new PluginDescriptorMetaData());
    myPluginXML.setUrl(VfsUtil.pathToUrl(pluginXMLUrl.replace(File.separatorChar, '/')));
    myPluginXML.createIfNotExists();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myVirtualFilePointer = VirtualFilePointerManager.getInstance().create(LocalFileSystem.getInstance().refreshAndFindFileByPath(pluginXMLUrl.replace(File.separatorChar, '/')),
                                                                              null);
      }
    });
  }

}