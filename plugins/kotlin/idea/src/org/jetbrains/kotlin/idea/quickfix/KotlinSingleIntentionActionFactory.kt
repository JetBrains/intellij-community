// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic

abstract class KotlinSingleIntentionActionFactory : KotlinIntentionActionsFactory() {
    protected abstract fun createAction(diagnostic: Diagnostic): IntentionAction?

    final override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> = listOfNotNull(createAction(diagnostic))
}
