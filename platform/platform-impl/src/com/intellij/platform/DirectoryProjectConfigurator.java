// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface DirectoryProjectConfigurator {
  ExtensionPointName<DirectoryProjectConfigurator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectConfigurator");

  /**
   * @deprecated in favour of {@link #configureProject(Project, VirtualFile, Ref, boolean)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  default void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef) {
  }

  /**
   * @param newProject if true then new project created, existing folder opened otherwise
   */
  default void configureProject(@NotNull Project project,
                                @NotNull VirtualFile baseDir,
                                @NotNull Ref<Module> moduleRef,
                                boolean newProject) {
    //TODO: Remove default impl. in 2020.2
    configureProject(project, baseDir, moduleRef);
  }
}
