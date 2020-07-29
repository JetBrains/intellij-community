// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class ModifiableModelCommitter {
  public static void multiCommit(ModifiableRootModel @NotNull [] rootModels, @NotNull ModifiableModuleModel moduleModel) {
    multiCommit(Arrays.asList(rootModels), moduleModel);
  }

  public static void multiCommit(@NotNull Collection<? extends ModifiableRootModel> rootModels,
                                 @NotNull ModifiableModuleModel moduleModel) {
    Project project = moduleModel.getProject();
    ModifiableModelCommitterService.getInstance(project).multiCommit(rootModels, moduleModel);
  }
}
