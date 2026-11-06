// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.importing

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.gradle.scripting.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntity
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.extensions.GradleBaseSyncExtension


@Order(GradleBaseSyncExtension.ORDER - 1000)
internal class KotlinDslScriptSyncExtension : GradleSyncExtension {

    /**
     * Clears stale [GradleKotlinScriptEntitySource] entities from [projectStorage] at
     * [GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE], running before [GradleBaseSyncExtension].
     *
     * Entities from a previous sync carry `phase = SCRIPT_MODEL_PHASE`, which is later than
     * [GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE], so [GradleBaseSyncExtension] skips them.
     * This extension removes them explicitly so the base extension can write a fresh base-phase
     * snapshot without stale SCRIPT_MODEL entities lingering in [projectStorage].
     *
     * At [GradleSyncPhase.SCRIPT_MODEL_PHASE], removes generic fallback entities for scripts
     * in the sync snapshot so they do not shadow the configurations imported by Gradle.
     */
    override fun updateProjectModel(
        context: ProjectResolverContext,
        syncStorage: MutableEntityStorage,
        projectStorage: MutableEntityStorage,
        phase: GradleSyncPhase
    ) {
        if (phase == GradleSyncPhase.SCRIPT_MODEL_PHASE) {
            val importedScriptUrls = syncStorage.entities(KotlinScriptEntity::class.java)
                .filter { it.entitySource is GradleKotlinScriptEntitySource }
                .map { it.virtualFileUrl }
                .toSet()
            projectStorage.entities(KotlinScriptEntity::class.java)
                .filter { it.entitySource == KotlinScriptEntitySource && it.virtualFileUrl in importedScriptUrls }
                .toList()
                .forEach(projectStorage::removeEntity)
        }
        if (phase == GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE) {
            val kotlinScriptEntitySource = gradleKotlinScriptEntitySource(context)
            if (syncStorage.entitiesBySource(kotlinScriptEntitySource).any()) {
                projectStorage.replaceBySource(kotlinScriptEntitySource, ImmutableEntityStorage.empty())
            }
        }
    }
}
