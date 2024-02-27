// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KtValuesHintsProvider : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        val project = editor.project ?: file.project
        if (project.isDefault) return null

        return object : SharedBypassCollector {
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

                sink.addPresentation(InlineInlayPosition(leftExp.endOffset, true), hasBackground = true) {
                    text(leftText)
                }
                rightText?.let {
                    sink.addPresentation(InlineInlayPosition(rightExp.startOffset, true), hasBackground = true) {
                        text(it)
                    }
                }
            }
        }
    }

    context(KtAnalysisSession)
    private fun isApplicable(binaryExpression: KtBinaryExpression, leftExp: KtExpression, rightExp: KtExpression): Boolean {
        val functionCallOrNull = binaryExpression.resolveCall()?.singleFunctionCallOrNull()
        functionCallOrNull?.symbol?.takeIf {
            val packageName = it.callableIdIfNonLocal?.packageName
            packageName == StandardNames.RANGES_PACKAGE_FQ_NAME || packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME
        } ?: return false

        return leftExp.isComparable() && rightExp.isComparable()
    }

    context(KtAnalysisSession)
    private fun KtExpression.isComparable(): Boolean =
        when (this) {
            is KtConstantExpression -> true
            is KtBinaryExpression -> left?.isComparable() == true && right?.isComparable() == true
            else -> {
                val type = resolveCall()?.singleFunctionCallOrNull()?.symbol?.returnType
                    ?: ((this as? KtNameReferenceExpression)?.mainReference?.resolveToSymbol() as? KtCallableSymbol)?.returnType
                (type is KtNonErrorClassType) && (
                        type.classId in DefaultTypeClassIds.PRIMITIVES ||
                                type.getAllSuperTypes(true).any {
                                    val classTypeWithClassId = it.isClassTypeWithClassId(StandardClassIds.Comparable)
                                    classTypeWithClassId
                                })
            }
        }
}

@ApiStatus.Internal
enum class RangeKtExpressionType {
    RANGE_TO, RANGE_UNTIL, DOWN_TO, UNTIL
}

private fun KtOperationReferenceExpression.hasIllegalLiteralPrefixOrSuffix(): Boolean {
    val prevLeaf = PsiTreeUtil.prevLeaf(this)
    val nextLeaf = PsiTreeUtil.nextLeaf(this)
    return prevLeaf?.illegalLiteralPrefixOrSuffix() == true || nextLeaf?.illegalLiteralPrefixOrSuffix() == true
}

@ApiStatus.Internal
fun PsiElement.illegalLiteralPrefixOrSuffix(): Boolean {
    val elementType = this.node.elementType
    return (elementType === KtTokens.IDENTIFIER) ||
            (elementType === KtTokens.INTEGER_LITERAL) ||
            (elementType === KtTokens.FLOAT_LITERAL) ||
            elementType is KtKeywordToken
}

@ApiStatus.Internal
fun getRangeBinaryExpressionType(expression: KtExpression): RangeKtExpressionType? {
    val operationReference = (expression as? KtBinaryExpression)?.operationReference
    val binaryExprName = operationReference?.getReferencedNameAsName()?.asString()
    val dotQualifiedName = (expression as? KtDotQualifiedExpression)?.callExpression?.calleeExpression?.text
    val name = binaryExprName ?: dotQualifiedName
    return when {
        binaryExprName == ".." || dotQualifiedName == "rangeTo" -> RangeKtExpressionType.RANGE_TO
        binaryExprName == "..<" || dotQualifiedName == "rangeUntil" -> RangeKtExpressionType.RANGE_UNTIL
        name == "downTo" -> {
            if (operationReference?.hasIllegalLiteralPrefixOrSuffix() != false) return null
            RangeKtExpressionType.DOWN_TO
        }
        name == "until" -> {
            if (operationReference?.hasIllegalLiteralPrefixOrSuffix() != false) return null
            RangeKtExpressionType.UNTIL
        }
        else -> null
    }
}

@ApiStatus.Internal
fun KtExpression.getRangeLeftAndRightSigns(): Pair<String, String?>? {
    val type = getRangeBinaryExpressionType(this) ?: return null
    return when (type) {
        RangeKtExpressionType.RANGE_TO -> {
            KotlinBundle.message("hints.ranges.lessOrEqual") to KotlinBundle.message("hints.ranges.lessOrEqual")
        }

        RangeKtExpressionType.RANGE_UNTIL -> {
            KotlinBundle.message("hints.ranges.lessOrEqual") to null
        }

        RangeKtExpressionType.DOWN_TO -> {
            KotlinBundle.message("hints.ranges.greaterOrEqual") to KotlinBundle.message("hints.ranges.greaterOrEqual")
        }

        RangeKtExpressionType.UNTIL -> {
            KotlinBundle.message("hints.ranges.lessOrEqual") to KotlinBundle.message("hints.ranges.less")
        }
    }
}