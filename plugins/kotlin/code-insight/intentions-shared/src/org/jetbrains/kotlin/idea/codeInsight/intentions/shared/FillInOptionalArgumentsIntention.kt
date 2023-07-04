// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.utils.FillInArgumentsUtils.isApplicableByPsi
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.utils.FillInArgumentsUtils.findParameters
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.utils.FillInArgumentsUtils.fillInArguments
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.utils.FillInArgumentsUtils.getFunctionArguments
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class FillInOptionalArgumentsIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgumentList, FillInOptionalArgumentsIntention.Context>(KtValueArgumentList::class) {

    class Context(val optionalParametersList: List<Name>)

    override fun getFamilyName(): String = KotlinBundle.message("intention.fill.in.optional.arguments")

    override fun getActionName(element: KtValueArgumentList, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgumentList> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean {
        return isApplicableByPsi(element, true)
    }

    override fun apply(element: KtValueArgumentList, context: Context, project: Project, editor: Editor?) {
        fillInArguments(element, context.optionalParametersList)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgumentList): Context {
        val arguments = getFunctionArguments(element)
        val parameters = findParameters(element)
        val optionalParametersList = mutableListOf<Name>()
        for (param in parameters)  {
            if (param.hasDefaultValue()) {
                val parameterName = param.nameAsName
                if (parameterName != null && parameterName.asString() !in arguments)
                    optionalParametersList.add(parameterName)
            }
        }
        return Context(optionalParametersList.filter { it.asString() !in arguments })
    }
}