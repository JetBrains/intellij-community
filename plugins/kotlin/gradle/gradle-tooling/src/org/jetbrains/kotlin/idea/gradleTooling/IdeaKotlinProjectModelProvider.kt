// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKotlinProjectModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

object IdeaKotlinProjectModelProvider : ProjectImportModelProvider {
    override fun populateBuildModels(
        controller: BuildController,
        buildModel: GradleBuild,
        consumer: ProjectImportModelProvider.BuildModelConsumer
    ) = Unit

    override fun populateProjectModels(
        controller: BuildController,
        projectModel: Model,
        modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
    ) {
        controller.findModel(projectModel, IdeaKotlinProjectModel::class.java)?.apply {
            modelConsumer.consume(this, IdeaKotlinProjectModel::class.java)
        }
    }
}