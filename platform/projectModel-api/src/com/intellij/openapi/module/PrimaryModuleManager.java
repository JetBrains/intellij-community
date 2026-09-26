// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class PrimaryModuleManager {
  public static final ExtensionPointName<PrimaryModuleManager> EP_NAME = new ExtensionPointName<>("com.intellij.primaryModuleManager");

  /**
   * @return main (primary) module for IDE that supports project attach
   */
  public abstract @Nullable Module getPrimaryModule(@NotNull Project project);

  private static Module getPrimaryModuleByContentRoot(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return null;
    }
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return ContainerUtil.find(moduleManager.getModules(), module -> Arrays.asList(ModuleRootManager.getInstance(module).getContentRoots()).contains(baseDir));
  }

  /**
   * @return if exists, asks IDE customization to provide main/primary module,
   * otherwise use content roots of module to check if it's a main module.
   */
  public static Module findPrimaryModule(@NotNull Project project) {
    for (PrimaryModuleManager primaryModuleManager : EP_NAME.getExtensionList()) {
      Module primaryModule = primaryModuleManager.getPrimaryModule(project);
      if (primaryModule != null) {
        return primaryModule;
      }
    }
    return getPrimaryModuleByContentRoot(project);
  }
}
