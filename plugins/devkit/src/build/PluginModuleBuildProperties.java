/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import org.jetbrains.idea.devkit.module.ModuleSandboxManager;

public class PluginModuleBuildProperties extends ModuleBuildProperties implements ModuleComponent {
  private Module myModule;

  public PluginModuleBuildProperties(Module module) {
    myModule = module;
  }

  public String getArchiveExtension() {
    return "jar";
  }

  public String getJarPath() {
    return getSandboxManager().getSandbox().getSandboxHome() + "/plugins/" + myModule.getName();
  }

  public String getExplodedPath() {
    return getSandboxManager().getSandbox().getSandboxHome() + "/plugins/" + myModule.getName();
  }

  private ModuleSandboxManager getSandboxManager() {
    return ModuleSandboxManager.getInstance(myModule);
  }

  public Module getModule() {
    return myModule;
  }

  public boolean isJarEnabled() {
    return false;
  }

  public boolean isExplodedEnabled() {
    return isBuildActive();
  }

  public boolean isBuildOnFrameDeactivation() {
    //TODO
    return isBuildActive();
  }

  public boolean isSyncExplodedDir() {
    //TODO
    return isBuildActive();
  }

  private boolean isBuildActive() {
    return !getSandboxManager().isUnderIDEAProject();
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