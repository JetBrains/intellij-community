// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

@ApiStatus.Internal
class GradleTaskModelProvider : ProjectImportModelProvider {

  override fun populateModels(
    controller: BuildController,
    buildModels: Collection<GradleBuild>,
    modelConsumer: GradleModelConsumer,
  ) {
    GradleModelProviderUtil.buildModels(controller, buildModels, GradleTaskModel::class.java, modelConsumer)
  }
}
