// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class ModulePointerManager {
  public static ModulePointerManager getInstance(@NotNull Project project) {
    return project.getService(ModulePointerManager.class);
  }

  public abstract @NotNull ModulePointer create(@NotNull Module module);

  public abstract @NotNull ModulePointer create(@NotNull String moduleName);
}
