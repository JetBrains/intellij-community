// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

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
  /**
   * @deprecated Use {@link #configureProject(Project, VirtualFile, Ref, boolean)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  default void configureProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Ref<Module> moduleRef) {
  }

  default boolean isEdtRequired() {
    return true;
  }

  /**
   * @param isNewProject if true then new project created, existing folder opened otherwise
   */
  default void configureProject(@NotNull Project project,
                                @NotNull VirtualFile baseDir,
                                @NotNull Ref<Module> moduleRef,
                                boolean isNewProject) {
    // todo: remove default impl in 2020.2
    configureProject(project, baseDir, moduleRef);
  }
}
