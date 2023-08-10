// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class ModulePointerManager {
  public static ModulePointerManager getInstance(@NotNull Project project) {
    return project.getService(ModulePointerManager.class);
  }

  @NotNull
  public abstract ModulePointer create(@NotNull Module module);

  @NotNull
  public abstract ModulePointer create(@NotNull String moduleName);
}
