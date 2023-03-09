// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertStringToCharLiteralFix(element: KtStringTemplateExpression) : KotlinQuickFixAction<KtStringTemplateExpression>(element) {
    override fun getText() = KotlinBundle.message("convert.string.to.character.literal")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val stringTemplate = element ?: return
        val stringTemplateEntry = stringTemplate.applicableEntry ?: return
        stringTemplate.replace(stringTemplateEntry.charLiteral())
    }

    companion object {
        fun isApplicable(stringTemplate: KtStringTemplateExpression): Boolean {
            val stringTemplateEntry = stringTemplate.applicableEntry ?: return false
            val charLiteral = stringTemplateEntry.charLiteral() as? KtConstantExpression ?: return false
            val context = charLiteral.analyzeAsReplacement(stringTemplate, stringTemplate.analyze(BodyResolveMode.PARTIAL))
            return ConstantExpressionEvaluator.getConstant(charLiteral, context) != null
        }

        private val KtStringTemplateExpression.applicableEntry: KtStringTemplateEntry?
            get() = entries.singleOrNull().takeIf { it is KtLiteralStringTemplateEntry || it is KtEscapeStringTemplateEntry }

        private fun KtStringTemplateEntry.charLiteral(): KtExpression {
            val text = text.replace("'", "\\'").replace("\\\"", "\"")
            return KtPsiFactory(project).createExpression("'$text'")
        }
    }
}
