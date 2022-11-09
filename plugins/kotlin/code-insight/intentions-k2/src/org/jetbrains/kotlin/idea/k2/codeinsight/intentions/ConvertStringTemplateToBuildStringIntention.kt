// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isAnnotationArgument
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertStringTemplateToBuildStringCall
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ConvertStringTemplateToBuildStringIntention : AbstractKotlinApplicableIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class
), LowPriorityAction {
    override fun getFamilyName(): String = KotlinBundle.message("convert.string.template.to.build.string")
    override fun getActionName(element: KtStringTemplateExpression): String = familyName

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean =
        !element.text.startsWith("\"\"\"") && !element.isAnnotationArgument()

    override fun apply(element: KtStringTemplateExpression, project: Project, editor: Editor?) {
        val buildStringCall = convertStringTemplateToBuildStringCall(element)
        shortenReferences(buildStringCall)
    }
}