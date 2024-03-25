// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassBuildModelProvider
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.idea.gradle.scripting.importing.KotlinDslScriptModelResolverCommon
import org.jetbrains.kotlin.idea.gradleJava.scripting.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptModelProvider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

class KotlinDslScriptModelResolver : KotlinDslScriptModelResolverCommon() {

    override fun getModelProviders() = listOf(
        GradleClassBuildModelProvider(KotlinDslScriptAdditionalTask::class.java, GradleModelFetchPhase.PROJECT_LOADED_PHASE),
        KotlinDslScriptModelProvider()
    )

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getExtraProjectModelClasses())}
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getToolingExtensionsClasses())}
    }
}

class KotlinDslScriptModelContributor : ProjectModelContributor {
    override fun accept(resolverCtx: ProjectResolverContext) {
        for (buildModel in resolverCtx.allBuilds) {
            for (projectModel in buildModel.projects) {
                val projectIdentifier = projectModel.projectIdentifier.projectPath
                if (projectIdentifier == ":") {
                    val gradleVersion = resolverCtx.projectGradleVersion
                    if (gradleVersion != null && kotlinDslScriptsModelImportSupported(gradleVersion)) {
                        val model = resolverCtx.getProjectModel(projectModel, KotlinDslScriptsModel::class.java)
                        if (model != null) {
                            if (!processScriptModel(resolverCtx, model, projectIdentifier)) {
                                continue
                            }
                        }
                    }

                    saveGradleBuildEnvironment(resolverCtx)
                }
            }
        }
    }
}
