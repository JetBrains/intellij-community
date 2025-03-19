// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object IsEnumEntryFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement.safeAs<KtTypeReference>()?.parent ?: return null
        return when (element) {
            is KtIsExpression -> if (element.typeReference == null) null else ReplaceIsEnumEntryWithComparisonFix(element).asIntention()
            is KtWhenConditionIsPattern -> if (element.typeReference == null || element.isNegated) null else RemoveIsFromIsEnumEntryFix(element).asIntention()
            else -> null
        }
    }
}
