// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

class RemoveEmptySecondaryConstructorBodyInspection :
    AbstractApplicabilityBasedInspection<KtBlockExpression>(KtBlockExpression::class.java), CleanupLocalInspectionTool {
    override fun isApplicable(element: KtBlockExpression): Boolean {
        if (element.parent !is KtSecondaryConstructor) return false
        if (element.statements.isNotEmpty()) return false

        return element.text.replace("{", "").replace("}", "").isBlank()
    }

    override fun applyTo(element: KtBlockExpression, project: Project, editor: Editor?) = element.delete()

    override fun inspectionText(element: KtBlockExpression): String = KotlinBundle.message("remove.empty.constructor.body")

    override val defaultFixText: String
        get() = KotlinBundle.message("remove.empty.constructor.body")
}