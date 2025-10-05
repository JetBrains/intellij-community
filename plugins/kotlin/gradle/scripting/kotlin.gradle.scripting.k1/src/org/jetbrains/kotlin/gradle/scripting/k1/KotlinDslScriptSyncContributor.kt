// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import org.jetbrains.kotlin.gradle.scripting.shared.importing.collectErrors
import org.jetbrains.kotlin.gradle.scripting.shared.importing.getKotlinDslScripts
import org.jetbrains.kotlin.gradle.scripting.shared.importing.kotlinDslSyncListenerInstance
import org.jetbrains.kotlin.gradle.scripting.shared.importing.saveGradleBuildEnvironment
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class KotlinDslScriptSyncContributor : GradleSyncContributor {

    override val name: String = "Kotlin DSL Script"

    override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

    override suspend fun createProjectModel(
        context: ProjectResolverContext,
        storage: ImmutableEntityStorage
    ): ImmutableEntityStorage {
        val taskId = context.externalSystemTaskId
        val tasks = kotlinDslSyncListenerInstance?.tasks ?: return storage
        val sync = synchronized(tasks) { tasks[taskId] }

        val models = getKotlinDslScripts(context).toList()

        if (sync != null) {
            synchronized(sync) {
                sync.models.addAll(models)
                if (models.collectErrors().any()) {
                    sync.failed = true
                }
            }
        }

        saveGradleBuildEnvironment(context)

        return storage
    }
}