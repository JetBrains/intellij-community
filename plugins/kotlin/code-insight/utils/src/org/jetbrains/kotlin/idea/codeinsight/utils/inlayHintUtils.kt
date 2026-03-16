package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings

@ApiStatus.Internal
fun ASTNode.isFollowedByNewLine(): Boolean {
    for (sibling in siblings()) {
        if (sibling.elementType != TokenType.WHITE_SPACE && sibling.psi !is PsiComment) {
            continue
        }
        if (sibling.elementType == TokenType.WHITE_SPACE && sibling.textContains('\n')) {
            return true
        }
    }
    return false
}

@ApiStatus.Internal
enum class RangeKtExpressionType {
    RANGE_TO, RANGE_UNTIL, DOWN_TO, UNTIL
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