// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConvertedToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertToStringLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ToRawStringLiteralIntention : AbstractKotlinApplicableIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class
), LowPriorityAction {
    override fun getFamilyName() = KotlinBundle.message("convert.to.raw.string.literal")

    override fun getActionName(element: KtStringTemplateExpression) = familyName

    override fun apply(element: KtStringTemplateExpression, project: Project, editor: Editor?) = convertToStringLiteral(element, editor)

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtStringTemplateExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtStringTemplateExpression) = element.canBeConvertedToStringLiteral()
}