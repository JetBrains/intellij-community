// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.utilDummyModel;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

public class DummyModelBuilder extends AbstractModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return DummyModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    return new DefaultDummyModel();
  }
}
