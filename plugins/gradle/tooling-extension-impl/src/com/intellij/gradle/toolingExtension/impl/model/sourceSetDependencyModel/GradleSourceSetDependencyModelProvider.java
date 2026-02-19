// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex.GradleSourceSetArtifactBuildRequest;
import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleSourceSetDependencyModel;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.Collection;

@ApiStatus.Internal
public class GradleSourceSetDependencyModelProvider implements ProjectImportModelProvider {

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE;
  }

  @Override
  public void populateModels(
    @NotNull BuildController controller,
    @NotNull Collection<? extends GradleBuild> buildModels,
    @NotNull GradleModelConsumer modelConsumer
  ) {
    GradleModelProviderUtil.buildModelsRecursively(controller, buildModels, GradleDependencyDownloadPolicy.class, GradleModelConsumer.NOOP);
    GradleModelProviderUtil.buildModels(controller, buildModels, GradleSourceSetArtifactBuildRequest.class, GradleModelConsumer.NOOP);
    GradleModelProviderUtil.buildModels(controller, buildModels, GradleSourceSetDependencyModel.class, modelConsumer);
  }
}
