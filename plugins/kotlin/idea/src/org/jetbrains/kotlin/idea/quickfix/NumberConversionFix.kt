// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.*

class NumberConversionFix(
    element: KtExpression,
    fromType: KotlinType,
    toType: KotlinType,
    private val disableIfAvailable: IntentionAction? = null
) : KotlinQuickFixAction<KtExpression>(element) {
    private val isConversionAvailable =
        fromType != toType && fromType.isSignedOrUnsignedNumberType() && toType.isSignedOrUnsignedNumberType()

    private val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(toType)

    private val fromInt = fromType.isInt()
    private val fromChar = fromType.isChar()
    private val fromFloatOrDouble = fromType.isFloat() || fromType.isDouble()

    private val toChar = toType.isChar()
    private val toInt = toType.isInt()
    private val toByteOrShort = toType.isByte() || toType.isShort()

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile) =
        disableIfAvailable?.isAvailable(project, editor, file) != true && isConversionAvailable

    override fun getFamilyName() = KotlinBundle.message("insert.number.conversion")
    override fun getText() = KotlinBundle.message("convert.expression.to.0", typePresentation)

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(file)
        val apiVersion = element.languageVersionSettings.apiVersion
        val expressionToInsert = when {
            fromChar && apiVersion >= ApiVersion.KOTLIN_1_5 ->
                if (toInt) {
                    psiFactory.createExpressionByPattern("$0.code", element)
                } else {
                    psiFactory.createExpressionByPattern("$0.code.to$1()", element, typePresentation)
                }
            !fromInt && toChar && apiVersion >= ApiVersion.KOTLIN_1_5 ->
                psiFactory.createExpressionByPattern("$0.toInt().toChar()", element)
            fromFloatOrDouble && toByteOrShort && apiVersion >= ApiVersion.KOTLIN_1_3 ->
                psiFactory.createExpressionByPattern("$0.toInt().to$1()", element, typePresentation)
            else ->
                psiFactory.createExpressionByPattern("$0.to$1()", element, typePresentation)
        }
        val newExpression = element.replaced(expressionToInsert)
        editor?.caretModel?.moveToOffset(newExpression.endOffset)
    }
}