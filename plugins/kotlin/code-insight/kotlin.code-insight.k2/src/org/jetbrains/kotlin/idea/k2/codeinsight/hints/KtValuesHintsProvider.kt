// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.StandardNames.BUILT_INS_PACKAGE_FQ_NAME
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_KOTLIN_TIME
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_RANGES
import org.jetbrains.kotlin.idea.codeInsight.hints.getRangeLeftAndRightSigns
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KtValuesHintsProvider : AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        collectForKotlinTime(element, sink)
        collectForRanges(element, sink)
    }

    private fun collectForKotlinTime(element: PsiElement, sink: InlayTreeSink) {
        val expression = element as? KtDotQualifiedExpression ?: return
        val selectorExpression = expression.selectorExpression ?: return
        sink.whenOptionEnabled(SHOW_KOTLIN_TIME.name) {
            val callableId = analyze(expression) {
                val variableAccessCall = expression.resolveToCall()?.singleVariableAccessCall()
                val symbol = variableAccessCall?.symbol?.takeIf {
                    val classId = it.callableId?.classId
                    classId == DURATION_CLASS_ID  || classId == DURATION_COMPANION_CLASS_ID
                } as? KaCallableSymbol ?: return@whenOptionEnabled
                symbol.callableId
            } ?: return@whenOptionEnabled
            when(callableId.callableName.asString()) {
                "days" -> {
                    sink.addPresentation(InlineInlayPosition(selectorExpression.startOffset, true), hintFormat = HintFormat.default) {
                        text("24h")
                    }
                }
                "inWholeDays" -> {
                    sink.addPresentation(InlineInlayPosition(selectorExpression.endOffset, true), hintFormat = HintFormat.default) {
                        text("24h")
                    }
                }
            }
        }
    }

    private fun collectForRanges(element: PsiElement, sink: InlayTreeSink) {
        val binaryExpression = element as? KtBinaryExpression ?: return
        val leftExp = binaryExpression.left ?: return
        val rightExp = binaryExpression.right ?: return

        val (leftText: String, rightText: String?) = binaryExpression.getRangeLeftAndRightSigns() ?: return

        sink.whenOptionEnabled(SHOW_RANGES.name) {
            val applicable = analyze(binaryExpression) {
                isApplicableForRanges(binaryExpression, leftExp, rightExp)
            }
            if (!applicable) return@whenOptionEnabled

            sink.addPresentation(InlineInlayPosition(leftExp.endOffset, true), hintFormat = HintFormat.default) {
                text(leftText)
            }
            rightText?.let {
                sink.addPresentation(InlineInlayPosition(rightExp.startOffset, true), hintFormat = HintFormat.default) {
                    text(it)
                }
            }
        }
    }

    private fun KaSession.isApplicableForRanges(binaryExpression: KtBinaryExpression, leftExp: KtExpression, rightExp: KtExpression): Boolean {
        val functionCallOrNull = binaryExpression.resolveToCall()?.singleFunctionCallOrNull()
        functionCallOrNull?.symbol?.takeIf {
            val packageName = it.callableId?.packageName
            packageName == StandardNames.RANGES_PACKAGE_FQ_NAME || packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME
        } ?: return false

        return isComparable(leftExp) && isComparable(rightExp)
    }

    private fun KaSession.isComparable(expression: KtExpression): Boolean =
        with(this) {
            when (expression) {
                is KtConstantExpression -> true
                is KtBinaryExpression -> {
                    val leftExpression = expression.left ?: return false
                    val rightExpression = expression.right ?: return false
                    isComparable(leftExpression) && isComparable(rightExpression)
                }

                else -> {
                    val type = expression.expressionType as? KaClassType ?: return false
                    type.classId in DefaultTypeClassIds.PRIMITIVES || type.isSubtypeOf(StandardClassIds.Comparable)
                }
            }
        }
}

private val KOTLIN_TIME_PACKAGE = BUILT_INS_PACKAGE_FQ_NAME.child(Name.identifier("time"))
private val DURATION_CLASS_ID = ClassId(KOTLIN_TIME_PACKAGE , Name.identifier("Duration"))
private val DURATION_COMPANION_CLASS_ID = ClassId(KOTLIN_TIME_PACKAGE, Name.identifier("Duration.Companion"))