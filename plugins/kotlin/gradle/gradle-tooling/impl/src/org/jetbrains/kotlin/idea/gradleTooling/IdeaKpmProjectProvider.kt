// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProjectContainer
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

object IdeaKpmProjectProvider : ProjectImportModelProvider {

    override fun populateProjectModels(
        controller: BuildController,
        projectModel: BasicGradleProject,
        modelConsumer: GradleModelConsumer
    ) {
        val container = controller.findModel(projectModel, IdeaKpmProjectContainer::class.java) ?: return
        modelConsumer.consumeProjectModel(projectModel, container, IdeaKpmProjectContainer::class.java)
    }
}

