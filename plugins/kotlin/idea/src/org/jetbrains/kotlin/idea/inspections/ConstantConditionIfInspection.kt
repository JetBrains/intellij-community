// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal fun KtExpression.replaceWithBranchAndMoveCaret(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false) {
    val originalExpression = this

    // TODO get rid of this caret model manipulation when all usages are migrated to Mod command - it doesn't work there 
    val caretModel = originalExpression.findExistingEditor()?.caretModel

    // This code can be called non-Mod command usages, so we have to allow calling it from EDT and write action
    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    val replaced = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            originalExpression.replaceWithBranch(branch, isUsedAsExpression, keepBraces)
        }
    }

    if (replaced != null) {
        caretModel?.moveToOffset(replaced.startOffset)
    }
}