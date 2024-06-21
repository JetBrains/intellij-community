// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.Collection;

@ApiStatus.Internal
public class GradleExternalProjectModelProvider implements ProjectImportModelProvider {

  private static final long serialVersionUID = 1L;

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_MODEL_PHASE;
  }

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    GradleModelProviderUtil.buildModels(controller, buildModels, ExternalProject.class, modelConsumer);
  }
}
