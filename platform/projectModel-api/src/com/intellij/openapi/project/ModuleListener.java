// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * Modules added, removed, or renamed in project.
 *
 * @see ProjectTopics#MODULES
 */
@ApiStatus.OverrideOnly
public interface ModuleListener extends EventListener {
  /**
   * @deprecated Use {@link #modulesAdded(Project, List)}
   */
  @Deprecated
  default void moduleAdded(@NotNull Project project, @NotNull Module module) {
  }

  default void modulesAdded(@NotNull Project project, @NotNull List<? extends Module> modules) {
    for (Module module : modules) {
      moduleAdded(project, module);
    }
  }

  default void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
  }

  default void moduleRemoved(@NotNull Project project, @NotNull Module module) {
  }

  default void modulesRenamed(@NotNull Project project,
                              @NotNull List<? extends Module> modules,
                              @NotNull Function<? super Module, String> oldNameProvider) {
  }
}
