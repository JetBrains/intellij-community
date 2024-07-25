// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ReplaceWithComparisonFix(isExpression: KtIsExpression) : KotlinQuickFixAction<KtIsExpression>(isExpression) {
    private val comparison = if (isExpression.isNegated) "!=" else "=="

    override fun getText() = KotlinBundle.message("replace.with.0", comparison)

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val leftHandSide = element?.leftHandSide ?: return
        val typeReference = element?.typeReference?.text ?: return
        val binaryExpression = KtPsiFactory(project).createExpressionByPattern("$0 $comparison $1", leftHandSide, typeReference)
        element?.replace(binaryExpression)
    }
}
