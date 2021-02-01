// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public interface ModuleListener extends EventListener {
  default void moduleAdded(@NotNull Project project, @NotNull Module module) {
  }

  default void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
  }

  default void moduleRemoved(@NotNull Project project, @NotNull Module module) {
  }

  default void modulesRenamed(@NotNull Project project, @NotNull List<? extends Module> modules, @NotNull Function<? super Module, String> oldNameProvider) {
  }
}
