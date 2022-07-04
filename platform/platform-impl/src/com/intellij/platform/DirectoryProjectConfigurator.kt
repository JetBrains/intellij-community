// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Configures various subsystems (facets etc.) when a user opens a directory with code but without `.idea` subdirectory.
 * <p>
 * Example: to support some framework, you need to enable and configure a facet. A user opens a directory with code for the first time.
 * This class scans the code and detects the framework heuristically. It then configures facet without user action.
 */
public interface DirectoryProjectConfigurator {
  /**
   * @return if code must be called or EDT or not.
   * If {@link #configureProject(Project, VirtualFile, Ref, boolean)} is slow (heavy computations, network access etc) return "false" here.
   */
  default boolean isEdtRequired() {
    return true;
  }

  /**
   * @param isProjectCreatedWithWizard if true then new project created with wizard, existing folder opened otherwise
   */
  void configureProject(@NotNull Project project,
                        @NotNull VirtualFile baseDir,
                        @NotNull Ref<Module> moduleRef,
                        boolean isProjectCreatedWithWizard);
}
