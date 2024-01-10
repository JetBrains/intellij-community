// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
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
    AbstractKotlinModCommandWithContext<KtValueArgument, AddNamesToFollowingArgumentsIntention.Context>(KtValueArgument::class),
    LowPriorityAction {

    class Context(val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>)

    override fun getFamilyName(): String = KotlinBundle.message("add.names.to.this.argument.and.following.arguments")
    override fun getActionName(element: KtValueArgument, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgument> =
        ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

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

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgument): Context? =
        element.parents.match(KtValueArgumentList::class, last = KtCallElement::class)
            ?.let { call -> associateArgumentNamesStartingAt(call, element) }
            ?.let(::Context)

    override fun apply(element: KtValueArgument, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        addArgumentNames(context.analyzeContext.argumentNames.dereferenceValidKeys())
    }
}