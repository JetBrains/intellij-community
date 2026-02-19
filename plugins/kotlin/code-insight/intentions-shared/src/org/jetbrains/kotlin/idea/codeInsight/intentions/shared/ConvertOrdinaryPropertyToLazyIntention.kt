// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.isCallingAnyOf
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal class ConvertOrdinaryPropertyToLazyIntention :
    KotlinApplicableModCommandAction<KtProperty, ConvertOrdinaryPropertyToLazyIntention.Context>(KtProperty::class) {

    internal class Context(
        val isRunCall: Boolean,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.lazy.property")

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        !element.isVar &&
                element.initializer != null &&
                element.getter == null &&
                !element.isLocal &&
                !element.hasModifier(KtTokens.CONST_KEYWORD)

    override fun KaSession.prepareContext(element: KtProperty): Context {
        val initializer = element.initializer as? KtCallExpression
        val isRunCall = initializer?.isCallingAnyOf(StandardKotlinNames.run) == true
        return Context(isRunCall)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val initializer = element.initializer ?: return
        val psiFactory = KtPsiFactory(element.project)
        val newExpression = if (elementContext.isRunCall) {
            if (initializer !is KtCallExpression) return
            initializer.calleeExpression?.replace(psiFactory.createExpression("lazy"))
            initializer
        } else {
            psiFactory.createExpressionByPattern("lazy { $0 }", initializer)
        }
        element.addAfter(psiFactory.createPropertyDelegate(newExpression), initializer)
        element.initializer = null
    }
}
