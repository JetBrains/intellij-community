// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSingleCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSymbol
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.AddQualifiersUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolution.KtResolvableCall

internal class AddFullQualifierIntention :
    KotlinApplicableModCommandAction<KtNameReferenceExpression, AddFullQualifierIntention.Context>(KtNameReferenceExpression::class) {

    data class Context(
        val fqName: FqName,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.full.qualifier")
    override fun getActionPresentation(context: ActionContext, element: KtNameReferenceExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    override fun prepareContext(element: KtNameReferenceExpression): Context? {
        val contextSymbol = ((element as? KtResolvableCall)?.resolveCall() as? KaSingleCall<*, *>)?.symbol ?: element.resolveSymbol()
        return if (contextSymbol != null && AddQualifiersUtil.isApplicableTo(element, contextSymbol)) {
            val fqName = AddQualifiersUtil.getFqName(contextSymbol)
            require(fqName != null)
            Context(fqName)
        } else {
            null
        }
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