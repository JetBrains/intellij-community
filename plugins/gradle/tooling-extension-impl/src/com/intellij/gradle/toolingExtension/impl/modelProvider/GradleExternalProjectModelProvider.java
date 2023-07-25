// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelProvider;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

public class GradleExternalProjectModelProvider implements ProjectImportModelProvider {

  private static final long serialVersionUID = 1L;

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_MODEL_PHASE;
  }

  @Override
  public void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull BuildModelConsumer consumer
  ) {
    ExternalProject rootProject = controller.findModel(buildModel, ExternalProject.class);
    if (rootProject != null) {
      consumer.consume(buildModel, rootProject, ExternalProject.class);
    }
  }
}
