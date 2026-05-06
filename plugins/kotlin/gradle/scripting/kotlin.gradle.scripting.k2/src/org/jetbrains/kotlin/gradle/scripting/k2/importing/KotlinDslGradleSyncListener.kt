// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class KotlinDslGradleSyncListener : GradleSyncListener {
    override fun onSyncPhaseCompleted(context: ProjectResolverContext, phase: GradleSyncPhase) {
        if (phase == GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE || phase == GradleSyncPhase.SCRIPT_MODEL_PHASE) {
            // Reload after BASE_SCRIPT_MODEL_PHASE so open scripts are reprocessed against fresh fallback definitions.
            // Reload again after SCRIPT_MODEL_PHASE to upgrade open scripts to precise Gradle-owned entities.
            // Closed files recover lazily when opened later via `KotlinScriptEditorListener`.
            KotlinScriptService.getInstance(context.project).scheduleReloadOpenScripts()
        }
    }
}
