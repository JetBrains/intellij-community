// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.modelAction.GradleModelController
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProjectContainer
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

object IdeaKpmProjectProvider : ProjectImportModelProvider {

    override fun populateModels(
        modelController: GradleModelController,
        buildModels: Collection<GradleBuild>,
        modelConsumer: GradleModelConsumer
    ) {
        modelController.fetchRequest(buildModels, IdeaKpmProjectContainer::class.java)
            .suppressFailures()
            .executionMode(GradleExecutionMode.SEQUENTIAL)
            .execute(modelConsumer)
    }
}

