// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import kotlin.math.floor

private val valueRanges = mapOf(
    StandardNames.FqNames._byte to Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong(),
    StandardNames.FqNames._short to Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong(),
    StandardNames.FqNames._int to Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(),
    StandardNames.FqNames._long to Long.MIN_VALUE..Long.MAX_VALUE
)

context(KtAnalysisSession)
class WrongPrimitiveLiteralFix(element: KtExpression, type: KtType) : KotlinQuickFixAction<KtExpression>(element) {

    private val typeName = type.expandedClassSymbol?.classIdIfNonLocal?.asSingleFqName()?.toUnsafe()
    private val expectedTypeIsFloat = type.isFloat
    private val expectedTypeIsDouble = type.isDouble
    private val expectedTypeIsUnsigned = type.isUNumberType()

    private val constValue =
            element.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)?.value

    private val fixedExpression = buildString {
        if (expectedTypeIsFloat || expectedTypeIsDouble) {
            append(constValue)
            if (expectedTypeIsFloat) {
                append('F')
            } else if ('.' !in this) {
                append(".0")
            }
        } else if (expectedTypeIsUnsigned) {
            append(constValue)
            append('u')
        } else {
            if (constValue is Float || constValue is Double) {
                append(toLong()!!)
            } else {
                append(element.text.trimEnd('l', 'L', 'u'))
            }

            if (type.isLong) {
                append('L')
            }
        }
    }

    private fun toLong(): Long? = when (constValue) {
        is Number -> constValue.toLong()
        is UByte -> constValue.toLong()
        is UShort -> constValue.toLong()
        is UInt -> constValue.toLong()
        is ULong -> constValue.toLong()
        else -> null
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        if (constValue == null) return false
        val longValue = toLong() ?: return false
        if (expectedTypeIsFloat || expectedTypeIsDouble || expectedTypeIsUnsigned) return true

        if (constValue is Float || constValue is Double) {
            val value = (constValue as? Float)?.toDouble() ?: constValue as Double
            if (value != floor(value)) return false
            if (value !in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) return false
        }

        return longValue in (valueRanges[typeName] ?: return false)
    }

    override fun getFamilyName() = KotlinBundle.message("change.to.correct.primitive.type")
    override fun getText() = KotlinBundle.message("change.to.0", fixedExpression)

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val expressionToInsert = KtPsiFactory(project).createExpression(fixedExpression)
        val newExpression = element.replaced(expressionToInsert)
        editor?.caretModel?.moveToOffset(newExpression.endOffset)
    }
}