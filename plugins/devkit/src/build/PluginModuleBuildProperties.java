/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

public class PluginModuleBuildProperties extends ModuleBuildProperties implements ModuleComponent {
  private Module myModule;
  private boolean myJarPlugin = false;

  public PluginModuleBuildProperties(Module module) {
    myModule = module;
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
}