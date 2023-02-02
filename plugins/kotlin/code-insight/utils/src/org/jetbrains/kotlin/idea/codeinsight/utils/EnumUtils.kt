package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.psi.*

val ENUM_STATIC_METHOD_NAMES = listOf(StandardNames.ENUM_VALUES, StandardNames.ENUM_VALUE_OF)

/**
 * 'EnumClass.(values/valueOf/ect)' pattern is passed to the function. But receiver expression isn't checked
 */
fun KtQualifiedExpression.canBeReferenceToBuiltInEnumFunction(): Boolean {
    val callExpression = this.callExpression
    val callExpressionName = (callExpression?.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName()
    if (callExpressionName == StandardNames.ENUM_VALUES && callExpression.valueArguments.isEmpty()) return true
    if (callExpressionName == StandardNames.ENUM_VALUE_OF && callExpression.valueArguments.size == 1) return true
    val selectorExpressionName = (this.selectorExpression as? KtSimpleNameExpression)?.getReferencedNameAsName()
    if (selectorExpressionName in ENUM_STATIC_METHOD_NAMES && this.parentOfType<KtImportDirective>() != null) return true
    return false
}

fun KtCallableReferenceExpression.canBeReferenceToBuiltInEnumFunction(): Boolean {
    return this.callableReference.getReferencedNameAsName() in ENUM_STATIC_METHOD_NAMES
}