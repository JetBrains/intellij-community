// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.ArrayDeque;
import java.util.Queue;

public class GradleDependencyDownloadPolicyProvider implements ProjectImportModelProvider {

  @Override
  public GradleModelFetchPhase getPhase() {
    return GradleModelFetchPhase.DEPENDENCY_DOWNLOAD_POLICY_PHASE;
  }

  @Override
  public void populateBuildModels(
    @NotNull BuildController controller,
    @NotNull GradleBuild buildModel,
    @NotNull BuildModelConsumer consumer
  ) {
    Queue<BasicGradleProject> queue = new ArrayDeque<>();
    BasicGradleProject rootGradleProject = buildModel.getRootProject();
    // initializes policy in cache
    controller.findModel(rootGradleProject, GradleDependencyDownloadPolicy.class);
    queue.add(rootGradleProject);
    while (!queue.isEmpty()) {
      BasicGradleProject parentGradleProject = queue.remove();
      for (BasicGradleProject childGradleProject : parentGradleProject.getChildren()) {
        // initializes policy in cache
        controller.findModel(childGradleProject, GradleDependencyDownloadPolicy.class);
        queue.add(childGradleProject);
      }
    }
  }
}
