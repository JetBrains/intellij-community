// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.PrefillNamedParametersUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.PrefillNamedParametersUtils.prefillName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class PrefillNamedMandatoryParametersIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgumentList, PrefillNamedMandatoryParametersIntention.Context>(KtValueArgumentList::class) {

    class Context(val mandatoryParametersList: List<Name>)

    override fun getFamilyName(): String = KotlinBundle.message("intention.prefill.named.mandatory.parameters")

    override fun getActionName(element: KtValueArgumentList, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgumentList> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean {
        return element.arguments.isEmpty()
    }

    override fun apply(element: KtValueArgumentList, context: Context, project: Project, editor: Editor?) {
        prefillName(element, context.mandatoryParametersList)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgumentList): Context {
        val parameters = PrefillNamedParametersUtils.findParameters(element)
        val mandatoryParametersList = mutableListOf<Name>()
        for (param in parameters)  {
            if (!param.hasDefaultValue()) {
                val parameterName = param.nameAsName ?: return Context(emptyList())
                mandatoryParametersList.add(parameterName)
            }
        }
        return Context(mandatoryParametersList)
    }
}