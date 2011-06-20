/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.WatchedRootsProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class MvcWatchedRootProvider implements WatchedRootsProvider {
  private final Project myProject;

  public MvcWatchedRootProvider(Project project) {
    myProject = project;
  }

  @NotNull
  public Set<String> getRootsToWatch() {
    final Set<String> result = new HashSet<String>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(module);
      if (framework == null) continue;

      final File sdkWorkDir = framework.getCommonPluginsDir(module);
      if (sdkWorkDir != null) {
        result.add(sdkWorkDir.getAbsolutePath());
      }

      File globalPluginsDir = framework.getGlobalPluginsDir(module);
      if (globalPluginsDir != null) {
        result.add(globalPluginsDir.getAbsolutePath());
      }
    }

    return result;
  }

}
