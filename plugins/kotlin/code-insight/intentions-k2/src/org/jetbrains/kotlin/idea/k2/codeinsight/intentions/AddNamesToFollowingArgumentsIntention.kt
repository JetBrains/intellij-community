// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.addArgumentNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.associateArgumentNamesStartingAt
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class AddNamesToFollowingArgumentsIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgument, AddNamesToFollowingArgumentsIntention.Context>(KtValueArgument::class),
    LowPriorityAction {

    class Context(val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>)

    override fun getFamilyName(): String = KotlinBundle.message("add.names.to.this.argument.and.following.arguments")
    override fun getActionName(element: KtValueArgument, context: Context): String = familyName

    override fun getApplicabilityRange() = ApplicabilityRanges.VALUE_ARGUMENT_EXCLUDING_LAMBDA

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
    override fun prepareContext(element: KtValueArgument): Context? {
        val argumentList = element.parent as? KtValueArgumentList ?: return null
        val call = argumentList.parent as? KtCallElement ?: return null
        return associateArgumentNamesStartingAt(call, element)?.let { Context(it) }
    }

    override fun apply(element: KtValueArgument, context: Context, project: Project, editor: Editor?) =
        addArgumentNames(context.argumentNames.dereferenceValidKeys())
}