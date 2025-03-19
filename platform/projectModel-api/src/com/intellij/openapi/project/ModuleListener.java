// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.util.Function;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * Modules added, removed, or renamed in project.
 */
@ApiStatus.OverrideOnly
public interface ModuleListener extends EventListener {
  @Topic.ProjectLevel
  Topic<ModuleListener> TOPIC = new Topic<>(ModuleListener.class, Topic.BroadcastDirection.NONE, true);

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
