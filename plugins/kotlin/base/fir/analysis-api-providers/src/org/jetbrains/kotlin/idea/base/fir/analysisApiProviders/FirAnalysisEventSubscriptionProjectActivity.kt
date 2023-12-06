// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionInvalidationService
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus

/**
 * [FirAnalysisEventSubscriptionProjectActivity] sets up subscriptions to events published via [analysisMessageBus] for FIR services. This
 * approach is favored over defining `projectListeners` in the plugin XML configuration for two reasons:
 *
 *  1. [analysisMessageBus] is not guaranteed to be the same as the [Project]'s message bus, by design, and `projectListeners` registers
 *     listeners with the project message bus.
 *  2. Services like [LLFirSessionInvalidationService] have to register listeners when running in the IDE, but also in Analysis API
 *     standalone mode. We cannot register listeners via XML in standalone mode, so this approach allows putting the subscription
 *     configuration in one place.
 */
internal class FirAnalysisEventSubscriptionProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LLFirSessionInvalidationService.getInstance(project).subscribeToModificationEvents()
    }
}
