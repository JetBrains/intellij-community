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
     * Manages the lifecycle of [GradleKotlinScriptEntitySource] entities across sync phases.
     *
     * [GradleKotlinScriptEntitySource] extends [org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource],
     * so [GradleBaseSyncExtension] processes these entities via its `shouldReplace` predicate.
     * However, the phase-comparison in `shouldReplace` (`entitySource.phase > currentPhase → skip`)
     * creates two problems that this extension resolves by running before [GradleBaseSyncExtension]:
     *
     * [GradleSyncPhase.BaseScript] phase – existing script entities from a previous sync carry
     * `phase = SCRIPT_MODEL_PHASE`, which is later than any [GradleSyncPhase.BaseScript] phase,
     * so the base extension skips them. To ensure a clean slate, this extension explicitly
     * removes all Gradle Kotlin DSL entities from [projectStorage] before the base extension runs.
     * The base extension then writes the fresh base-phase definition entities from [syncStorage].
     *
     * [GradleSyncPhase.Dynamic] phases before [GradleSyncPhase.SCRIPT_MODEL_PHASE] –
     * [syncStorage] accumulates entities within a phase class and is reset between classes.
     * Base-phase definition entities were written during the [GradleSyncPhase.BaseScript] class, so [syncStorage] for [GradleSyncPhase.Dynamic]
     * phases carries none of them. Without intervention, `replaceBySource` in [GradleBaseSyncExtension]
     * would delete the base-phase definition entities already present in [projectStorage]. To prevent
     * this, this extension first copies them from [projectStorage] into [syncStorage], so the
     * base extension sees and preserves them.
     *
     * [GradleSyncPhase.Dynamic] phases after [GradleSyncPhase.SCRIPT_MODEL_PHASE] (included) – no action needed.
     * The [syncStorage] already contains the final script-phase entities. The base extension replaces the base-phase entities with them.
     */
    override fun updateProjectModel(
        context: ProjectResolverContext,
        syncStorage: MutableEntityStorage,
        projectStorage: MutableEntityStorage,
        phase: GradleSyncPhase
    ) {
        if (phase is GradleSyncPhase.BaseScript) {
            projectStorage.replaceBySource(
                { it is GradleKotlinScriptEntitySource && it.projectPath == context.projectPath },
                ImmutableEntityStorage.empty()
            )
        }
        if (phase is GradleSyncPhase.Dynamic && phase < GradleSyncPhase.SCRIPT_MODEL_PHASE) {
            syncStorage.replaceBySource(
                { it is GradleKotlinScriptEntitySource && it.projectPath == context.projectPath },
                projectStorage
            )
        }
    }
}
