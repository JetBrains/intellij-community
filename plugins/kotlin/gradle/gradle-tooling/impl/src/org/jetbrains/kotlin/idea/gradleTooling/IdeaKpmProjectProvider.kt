// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.impl.util.GradleModelProviderUtil
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProjectContainer
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

object IdeaKpmProjectProvider : ProjectImportModelProvider {

    override fun populateModels(
        controller: BuildController,
        buildModels: Collection<GradleBuild>,
        modelConsumer: GradleModelConsumer
    ) {
        GradleModelProviderUtil.buildModelsInSequence(controller, buildModels, IdeaKpmProjectContainer::class.java, modelConsumer)
    }
}

