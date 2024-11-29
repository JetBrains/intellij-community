// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentName
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.getStableNameFor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class AddNameToArgumentIntention :
    KotlinApplicableModCommandAction<KtValueArgument, AddNameToArgumentIntention.Context>(KtValueArgument::class) {

    data class Context(
        val argumentName: Name,
    )

    override fun getFamilyName(): String =
        KotlinBundle.message("add.name.to.argument")

    override fun getPresentation(
        context: ActionContext,
        element: KtValueArgument,
    ): Presentation? {
        val (argumentName) = getElementContext(context, element)
            ?: return null
        return Presentation.of(KotlinBundle.message("add.0.to.argument", argumentName))
            .withPriority(PriorityAction.Priority.LOW)
    }

    override fun getApplicableRanges(element: KtValueArgument): List<TextRange> =
        ApplicabilityRanges.valueArgumentExcludingLambda(element)

    override fun isApplicableByPsi(element: KtValueArgument): Boolean {
        if (element.isNamed()) return false

        // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`); element is a KtLambdaArgument.
        // Note: IS applicable when lambda is inside an argument list (e.g., `run({  })`); element is a KtValueArgument in this case.
        if (element is KtLambdaArgument) return false

        // Either mixed named arguments must be allowed or the element must be the last unnamed argument.
        val argumentList = element.parent as? KtValueArgumentList ?: return false
        return element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition) ||
                element == argumentList.arguments.last { !it.isNamed() }
    }

    context(KaSession)
    override fun prepareContext(element: KtValueArgument): Context? {
        return getStableNameFor(element)?.let { Context(it) }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgument,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        addArgumentName(element, elementContext.argumentName)
    }

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
        return element is KtValueArgumentList || element is KtContainerNode || super.stopSearchAt(element, context)
    }
}