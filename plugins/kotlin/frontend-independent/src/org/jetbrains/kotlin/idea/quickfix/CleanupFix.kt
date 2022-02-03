// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction

/**
 * Marker interface for quickfixes that can be used as part of the "Cleanup Code" action. The diagnostics
 * that produce these quickfixes need to be added to KotlinCleanupInspection.cleanupDiagnosticsFactories.
 */
interface CleanupFix : IntentionAction {
}
// TODO(yole): add isSafeToApply() method here to get rid of filtering by diagnostics factories in
// KotlinCleanupInspection
