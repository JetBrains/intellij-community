// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.canRemoveByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.removeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgumentList

class RemoveEmptyParenthesesFromLambdaCallInspection : AbstractKotlinApplicableInspection<KtValueArgumentList>(KtValueArgumentList::class) {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.display.name")
    override fun getActionName(element: KtValueArgumentList): String =
        KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.action.name")

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean = canRemoveByPsi(element)

    override fun apply(element: KtValueArgumentList, project: Project, editor: Editor?) {
        removeArgumentList(element)
    }
}