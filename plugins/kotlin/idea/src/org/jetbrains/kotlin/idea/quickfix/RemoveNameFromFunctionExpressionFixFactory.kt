// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext

internal object RemoveNameFromFunctionExpressionFixFactory : KotlinSingleIntentionActionFactory() {

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement.getNonStrictParentOfType<KtNamedFunction>() ?: return null

        var wereAutoLabelUsages = false
        val name = element.nameAsName ?: return null

        element.forEachDescendantOfType<KtReturnExpression> {
            if (!wereAutoLabelUsages && it.getLabelNameAsName() == name) {
                wereAutoLabelUsages = it.analyze().get(BindingContext.LABEL_TARGET, it.getTargetLabel()) == element
            }
        }

        return RemoveNameFromFunctionExpressionFix(element, wereAutoLabelUsages).asIntention()
    }
}
