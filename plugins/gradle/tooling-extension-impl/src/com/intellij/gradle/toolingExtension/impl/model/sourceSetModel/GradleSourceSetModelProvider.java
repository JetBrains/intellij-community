// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.model.warmUp.GradleTaskWarmUpRequest;
import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.Collection;

@ApiStatus.Internal
public class GradleSourceSetModelProvider implements ProjectImportModelProvider {

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE;
  }

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    GradleModelProviderUtil.buildModels(controller, buildModels, GradleTaskWarmUpRequest.class, GradleModelConsumer.NOOP);
    GradleModelProviderUtil.buildModels(controller, buildModels, GradleSourceSetModel.class, modelConsumer);
  }
}
