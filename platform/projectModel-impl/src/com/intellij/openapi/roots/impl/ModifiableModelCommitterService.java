// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public interface ModifiableModelCommitterService {
  void multiCommit(@NotNull Collection<? extends ModifiableRootModel> rootModels, @NotNull ModifiableModuleModel moduleModel);

  static ModifiableModelCommitterService getInstance(Project project) {
    return project.getService(ModifiableModelCommitterService.class);
  }
}
