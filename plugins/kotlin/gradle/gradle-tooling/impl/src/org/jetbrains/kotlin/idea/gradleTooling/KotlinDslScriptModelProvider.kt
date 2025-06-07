// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer

class KotlinDslScriptModelProvider : ProjectImportModelProvider {
    private val kotlinDslScriptModelClass: Class<*> = KotlinDslScriptsModel::class.java

    override fun populateModels(
        controller: BuildController,
        buildModels: Collection<GradleBuild>,
        modelConsumer: GradleModelConsumer
    ) {
        buildModels.flatMap { it.projects }.forEach {
            if (it.parent == null) {
                try {
                    val model = controller.findModel(it, kotlinDslScriptModelClass)
                    if (model != null) {
                        modelConsumer.consumeProjectModel(it, model, kotlinDslScriptModelClass)
                    }
                } catch (e: Throwable) {
                    modelConsumer.consumeProjectModel(
                        it,
                        BrokenKotlinDslScriptsModel(e), kotlinDslScriptModelClass
                    )
                }
            }
        }
    }
}