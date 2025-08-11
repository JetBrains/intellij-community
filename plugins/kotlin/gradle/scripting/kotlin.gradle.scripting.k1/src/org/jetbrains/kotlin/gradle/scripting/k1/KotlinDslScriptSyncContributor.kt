// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1

import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.gradle.scripting.shared.importing.kotlinDslSyncListenerInstance
import org.jetbrains.kotlin.gradle.scripting.shared.importing.processScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.importing.saveGradleBuildEnvironment
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class KotlinDslScriptSyncContributor : GradleSyncContributor {

    override val name: String = "Kotlin DSL Script"

    override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

    override suspend fun updateProjectModel(
        context: ProjectResolverContext,
        storage: MutableEntityStorage
    ) {
        val taskId = context.externalSystemTaskId
        val tasks = kotlinDslSyncListenerInstance?.tasks ?: return
        val sync = synchronized(tasks) { tasks[taskId] }

        for (buildModel in context.allBuilds) {
            for (projectModel in buildModel.projects) {
                val projectIdentifier = projectModel.projectIdentifier.projectPath
                if (projectIdentifier == ":") {
                    if (kotlinDslScriptsModelImportSupported(context.projectGradleVersion)) {
                        val model = context.getProjectModel(projectModel, KotlinDslScriptsModel::class.java)
                        if (model != null) {
                            if (!processScriptModel(context, sync, model, projectIdentifier)) {
                                continue
                            }
                        }
                    }

                    saveGradleBuildEnvironment(context)
                }
            }
        }
    }
}