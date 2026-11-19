// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.importing

import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class KotlinDslGradleSyncListener : GradleSyncListener {
    override fun onSyncPhaseCompleted(context: ProjectResolverContext, phase: GradleSyncPhase) {
        if (phase == GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE || phase == GradleSyncPhase.SCRIPT_MODEL_PHASE) {
            // Each phase replaces the Gradle-owned script entities, so the open scripts that lost theirs load again.
            // Closed files recover lazily when opened later via `KotlinScriptEditorListener`.
            KotlinScriptService.getInstance(context.project).scheduleLoadOpenScripts()
        }
    }
}
