// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.codeInsight.hints.getRangeLeftAndRightSigns
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KtValuesHintsProvider : AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        val binaryExpression = element as? KtBinaryExpression ?: return
        val leftExp = binaryExpression.left ?: return
        val rightExp = binaryExpression.right ?: return

        val (leftText: String, rightText: String?) = binaryExpression.getRangeLeftAndRightSigns() ?: return

        val applicable = analyze(binaryExpression) {
            isApplicable(binaryExpression, leftExp, rightExp)
        }
        if (!applicable) return

        sink.addPresentation(InlineInlayPosition(leftExp.endOffset, true), hintFormat = HintFormat.default) {
            text(leftText)
        }
        rightText?.let {
            sink.addPresentation(InlineInlayPosition(rightExp.startOffset, true), hintFormat = HintFormat.default) {
                text(it)
            }
        }
    }

    context(KaSession)
    private fun isApplicable(binaryExpression: KtBinaryExpression, leftExp: KtExpression, rightExp: KtExpression): Boolean {
        val functionCallOrNull = binaryExpression.resolveToCall()?.singleFunctionCallOrNull()
        functionCallOrNull?.symbol?.takeIf {
            val packageName = it.callableId?.packageName
            packageName == StandardNames.RANGES_PACKAGE_FQ_NAME || packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME
        } ?: return false

        return leftExp.isComparable() && rightExp.isComparable()
    }

    context(KaSession)
    private fun KtExpression.isComparable(): Boolean =
        when (this) {
            is KtConstantExpression -> true
            is KtBinaryExpression -> left?.isComparable() == true && right?.isComparable() == true
            else -> {
                val resolvedCall = resolveToCall()
                val type = resolvedCall?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol?.returnType
                    ?: ((this as? KtNameReferenceExpression)?.mainReference?.resolveToSymbol() as? KaCallableSymbol)?.returnType
                (type is KaClassType) && (
                        type.classId in DefaultTypeClassIds.PRIMITIVES ||
                                type.allSupertypes(true).any {
                                    val classTypeWithClassId = it.isClassType(StandardClassIds.Comparable)
                                    classTypeWithClassId
                                })
            }
        }
}