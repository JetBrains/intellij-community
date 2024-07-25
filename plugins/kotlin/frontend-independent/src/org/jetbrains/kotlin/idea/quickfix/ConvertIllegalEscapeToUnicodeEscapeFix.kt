// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class ConvertIllegalEscapeToUnicodeEscapeFix(
    element: KtElement,
    private val unicodeEscape: String
) : KotlinQuickFixAction<KtElement>(element) {
    override fun getText(): String = KotlinBundle.message("convert.to.unicode.escape")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = this.element ?: return
        val psiFactory = KtPsiFactory(project)
        when (element) {
            is KtConstantExpression -> element.replace(psiFactory.createExpression("'$unicodeEscape'"))
            is KtEscapeStringTemplateEntry -> element.replace(psiFactory.createStringTemplate(unicodeEscape).entries.first())
        }
    }
}
