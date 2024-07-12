// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for quickfixes that can be used as part of the "Cleanup Code" action. The diagnostics
 * that produce these quickfixes need to be added to KotlinCleanupInspection.cleanupDiagnosticsFactories.
 *
 * This interface should only be used for K1 compatability.
 */
interface CleanupFix : IntentionAction {
    @ApiStatus.Experimental
    interface ModCommand : ModCommandAction {
        override fun asIntention(): IntentionAction = object : IntentionWrapper(super.asIntention()), CleanupFix { }
    }
}