// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class AddArrayOfTypeFix(expression: KtExpression, private val prefix: String) : KotlinQuickFixAction<KtExpression>(expression) {

    override fun getText() = KotlinBundle.message("fix.add.array.of.type.text", prefix)
    override fun getFamilyName() = KotlinBundle.message("fix.add.array.of.type.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val arrayOfExpression = KtPsiFactory(project).createExpressionByPattern("$0($1)", prefix, element)
        element.replace(arrayOfExpression)
    }
}
