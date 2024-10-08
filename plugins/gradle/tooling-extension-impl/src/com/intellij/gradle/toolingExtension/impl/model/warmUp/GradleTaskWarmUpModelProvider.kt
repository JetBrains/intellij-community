// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.warmUp

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

@ApiStatus.Internal
class GradleTaskWarmUpModelProvider : ProjectImportModelProvider {

  override fun getPhase(): GradleModelFetchPhase {
    return GradleModelFetchPhase.WARM_UP_PHASE
  }

  override fun populateBuildModels(
    controller: BuildController,
    buildModel: GradleBuild,
    modelConsumer: GradleModelConsumer,
  ) {
    GradleModelProviderUtil.buildModels(controller, buildModel, GradleTaskWarmUpRequest::class.java, GradleModelConsumer.NOOP)
  }
}
