// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.addArgumentName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.getArgumentNameIfCanBeUsedForCalls
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class AddNameToArgumentIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgument, AddNameToArgumentIntention.Context>(KtValueArgument::class),
    LowPriorityAction {

    class Context(val argumentName: Name)

    override fun getFamilyName(): String = KotlinBundle.message("add.name.to.argument")

    override fun getActionName(element: KtValueArgument, context: Context): String =
        KotlinBundle.message("add.0.to.argument", context.argumentName)

    override fun getApplicabilityRange() = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

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

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgument): Context? {
        val callElement = element.parents.match(KtValueArgumentList::class, last = KtCallElement::class) ?: return null
        val resolvedCall = callElement.resolveCall().singleFunctionCallOrNull() ?: return null

        if (!resolvedCall.symbol.hasStableParameterNames) {
            return null
        }

        return getArgumentNameIfCanBeUsedForCalls(element, resolvedCall)?.let { Context(it) }
    }

    override fun apply(element: KtValueArgument, context: Context, project: Project, editor: Editor?) =
        addArgumentName(element, context.argumentName)

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtValueArgumentList || element is KtContainerNode || super.skipProcessingFurtherElementsAfter(element)
}