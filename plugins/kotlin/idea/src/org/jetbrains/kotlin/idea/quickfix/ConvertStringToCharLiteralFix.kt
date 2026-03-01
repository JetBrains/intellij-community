// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@K1Deprecation
class ConvertStringToCharLiteralFix(element: KtStringTemplateExpression) : KotlinQuickFixAction<KtStringTemplateExpression>(element) {
    override fun getText() = KotlinBundle.message("convert.string.to.character.literal")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val stringTemplate = element ?: return
        val charLiteral = ConvertStringToCharLiteralUtils.prepareCharLiteral(stringTemplate) ?: return
        stringTemplate.replace(charLiteral)
    }

    companion object {
        fun isApplicable(stringTemplate: KtStringTemplateExpression): Boolean {
            val charLiteral = ConvertStringToCharLiteralUtils.prepareCharLiteral(stringTemplate) ?: return false
            val context = charLiteral.analyzeAsReplacement(stringTemplate, stringTemplate.analyze(BodyResolveMode.PARTIAL))
            return ConstantExpressionEvaluator.getConstant(charLiteral, context) != null
        }
    }
}
