// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentNames
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.associateArgumentNamesStartingAt
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

internal class AddNamesToFollowingArgumentsIntention :
    KotlinApplicableModCommandAction<KtValueArgument, AddNamesToFollowingArgumentsIntention.Context>(KtValueArgument::class) {

    data class Context(
        val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.names.to.this.argument.and.following.arguments")
    override fun getPresentation(context: ActionContext, element: KtValueArgument): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun getApplicableRanges(element: KtValueArgument): List<TextRange> =
        ApplicabilityRanges.valueArgumentExcludingLambda(element)

    override fun isApplicableByPsi(element: KtValueArgument): Boolean {
        // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`); element is a KtLambdaArgument.
        // May be applicable when lambda is inside an argument list (e.g., `run({  })`); element is a KtValueArgument in this case.
        if (element.isNamed() || element is KtLambdaArgument) {
            return false
        }

        val argumentList = element.parent as? KtValueArgumentList ?: return false
        // Shadowed by `AddNamesToCallArgumentsIntention`
        if (argumentList.arguments.firstOrNull() == element) return false
        // Shadowed by `AddNameToArgumentIntention`
        if (argumentList.arguments.lastOrNull { !it.isNamed() } == element) return false

        return true
    }

    context(KaSession)
    override fun prepareContext(element: KtValueArgument): Context? =
        element.parents.match(KtValueArgumentList::class, last = KtCallElement::class)
            ?.let { call -> associateArgumentNamesStartingAt(call, element) }
            ?.let(::Context)

    override fun invoke(
      actionContext: ActionContext,
      element: KtValueArgument,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        addArgumentNames(elementContext.argumentNames.dereferenceValidKeys())
    }
}