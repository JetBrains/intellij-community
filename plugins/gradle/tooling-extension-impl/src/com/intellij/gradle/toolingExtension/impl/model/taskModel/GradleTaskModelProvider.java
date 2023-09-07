// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel;

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

@ApiStatus.Internal
public class GradleTaskModelProvider implements ProjectImportModelProvider {

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.TASK_WARM_UP_PHASE;
  }

  @Override
  public void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull BuildModelConsumer consumer
  ) {
    GradleModelProviderUtil.buildModels(controller, buildModel.getProjects(), GradleTaskModel.class);
  }
}
