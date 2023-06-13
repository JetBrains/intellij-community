// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.PrefillNamedParametersUtils.findParameters
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.PrefillNamedParametersUtils.prefillName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class PrefillNamedOptionalParametersIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgumentList, PrefillNamedOptionalParametersIntention.Context>(KtValueArgumentList::class) {

    class Context(val mandatoryParametersList: List<Name>)

    override fun getFamilyName(): String = KotlinBundle.message("intention.prefill.named.optional.parameters")

    override fun getActionName(element: KtValueArgumentList, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgumentList> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean {
        val arguments = element.children.map { it.firstChild.text }.toSet()
        val callElement = element.parent as KtCallElement
        val resolvedReference = callElement.calleeExpression?.mainReference?.resolve() ?: return false
        val paramsChildren = if (resolvedReference is KtFunction) {
            resolvedReference.valueParameters
        } else {
            return false
        }
        for (param in paramsChildren)  {
            val ktParam = (param as KtParameter)
            val parameterName = ktParam.name ?: return false
            if (ktParam.hasDefaultValue()) {
                if (parameterName !in arguments)
                    return true
            } else {
                if (parameterName !in arguments)
                    return false
            }
        }
        return false
    }

    override fun apply(element: KtValueArgumentList, context: Context, project: Project, editor: Editor?) {
        prefillName(element, context.mandatoryParametersList)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgumentList): Context {
        val arguments = element.arguments.mapNotNull { it.kotlinFqName?.shortName() }.toSet()
        val parameters = findParameters(element)
        val mandatoryParametersList = mutableListOf<Name>()
        for (param in parameters)  {
            if (param.hasDefaultValue()) {
                val parameterName = param.nameAsName
                if (parameterName != null && parameterName !in arguments)
                    mandatoryParametersList.add(parameterName)
            }
        }
        return Context(mandatoryParametersList)
    }
}