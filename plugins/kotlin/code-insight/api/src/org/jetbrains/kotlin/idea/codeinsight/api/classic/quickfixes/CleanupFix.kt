// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.codeInsight.intention.IntentionAction

/**
 * Marker interface for quickfixes that can be used as part of the "Cleanup Code" action. The diagnostics
 * that produce these quickfixes need to be added to KotlinCleanupInspection.cleanupDiagnosticsFactories.
 */
interface CleanupFix : IntentionAction