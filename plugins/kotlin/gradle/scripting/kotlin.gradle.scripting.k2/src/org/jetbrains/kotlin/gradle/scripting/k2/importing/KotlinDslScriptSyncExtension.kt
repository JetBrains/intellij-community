// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
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
     */
    override fun updateProjectModel(
        context: ProjectResolverContext,
        syncStorage: MutableEntityStorage,
        projectStorage: MutableEntityStorage,
        phase: GradleSyncPhase
    ) {
        if (phase == GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE) {
            val kotlinScriptEntitySource = gradleKotlinScriptEntitySource(context)
            if (syncStorage.entitiesBySource(kotlinScriptEntitySource).any()) {
                projectStorage.replaceBySource(kotlinScriptEntitySource, ImmutableEntityStorage.empty())
            }
        }
    }
}
