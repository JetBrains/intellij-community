/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.j2ee.j2eeDom.J2EEDeploymentItem;
import com.intellij.j2ee.j2eeDom.DeploymentDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.module.PluginDescriptorMetaData;
import org.jdom.Element;

import java.io.File;

public class PluginModuleBuildProperties extends ModuleBuildProperties implements ModuleComponent, JDOMExternalizable {
  private Module myModule;
  private J2EEDeploymentItem myPluginXML;
  public boolean myJarPlugin = false;
  public String myPluginXMLPath;
  public PluginModuleBuildProperties(Module module) {
    myModule = module;
    myPluginXMLPath = FileUtil.toSystemIndependentName(new File(myModule.getModuleFilePath()).getParent());
    myPluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(myModule, new PluginDescriptorMetaData());
    myPluginXML.setUrl(createPluginXMLURL(myPluginXMLPath));
    myPluginXML.createIfNotExists();
  }

  public static PluginModuleBuildProperties getInstance(Module module) {
    return (PluginModuleBuildProperties)module.getComponent(ModuleBuildProperties.class);
  }

  public String getArchiveExtension() {
    return "jar";
  }

  public String getJarPath() {
    return PluginBuildUtil.getPluginExPath(myModule) != null ? PluginBuildUtil.getPluginExPath(myModule) + "/lib/" + myModule.getName() + ".jar" : null;
  }

  public String getExplodedPath() {
    return PluginBuildUtil.getPluginExPath(myModule);
  }

  public boolean isJarPlugin() {
    return myJarPlugin;
  }

  public void setJarPlugin(boolean jarPlugin) {
    myJarPlugin = jarPlugin;
  }

  public Module getModule() {
    return myModule;
  }

  public boolean isJarEnabled() {
    return myJarPlugin;
  }

  public boolean isExplodedEnabled() {
    return !myJarPlugin;
  }

  public boolean isBuildOnFrameDeactivation() {
    //TODO
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
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public J2EEDeploymentItem getPluginXML() {
    return myPluginXML;
  }

  public String getPluginXMLPath() {
    return FileUtil.toSystemDependentName(myPluginXMLPath);
  }

  public void setPluginXMLPath(String pluginXMLPath) {
    myPluginXMLPath = FileUtil.toSystemIndependentName(pluginXMLPath);
    myPluginXML = DeploymentDescriptorFactory.getInstance().createDeploymentItem(myModule, new PluginDescriptorMetaData());
    myPluginXML.setUrl(createPluginXMLURL(myPluginXMLPath));
    myPluginXML.createIfNotExists();
  }

  private String createPluginXMLURL(String path){
    return VirtualFileManager.constructUrl("file", StringUtil.replace(path + "/META-INF/plugin.xml", File.separator, "/"));
  }
}