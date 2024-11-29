// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.AddQualifiersUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class AddFullQualifierIntention :
    KotlinApplicableModCommandAction<KtNameReferenceExpression, AddFullQualifierIntention.Context>(KtNameReferenceExpression::class) {

    data class Context(
        val fqName: FqName,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.full.qualifier")
    override fun getPresentation(context: ActionContext, element: KtNameReferenceExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    context(KaSession)
    override fun prepareContext(element: KtNameReferenceExpression): Context? {
        val contextSymbol = element.mainReference.resolveToSymbols().singleOrNull()
        if (contextSymbol != null && AddQualifiersUtil.isApplicableTo(element, contextSymbol)) {
            val fqName = AddQualifiersUtil.getFqName(contextSymbol)
            require(fqName != null)
            return Context(fqName)
        }
        return null
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtNameReferenceExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        AddQualifiersUtil.applyTo(element, elementContext.fqName)
    }
}