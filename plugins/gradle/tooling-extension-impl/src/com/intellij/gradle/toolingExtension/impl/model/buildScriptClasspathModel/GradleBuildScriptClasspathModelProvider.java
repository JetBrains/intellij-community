// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel;

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.Collection;

@ApiStatus.Internal
public class GradleBuildScriptClasspathModelProvider implements ProjectImportModelProvider {

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends @NotNull GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    GradleModelProviderUtil.buildModelsRecursively(controller, buildModels, GradleBuildScriptClasspathModel.class, modelConsumer);
  }
}
