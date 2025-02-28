// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

object RecursivePropertyAccessUtil {
    @ApiStatus.ScheduledForRemoval
    @Deprecated(
        "use isRecursivePropertyAccess(KtElement, Boolean) instead",
        replaceWith = ReplaceWith("isRecursivePropertyAccess(element, false)")
    )
    fun isRecursivePropertyAccess(element: KtElement): Boolean = isRecursivePropertyAccess(element, false)

    fun isRecursivePropertyAccess(element: KtElement, anyRecursionTypes: Boolean): Boolean {
        if (element !is KtSimpleNameExpression) return false
        return isApplicablePropertyAccessPsi(element) && element.isRecursivePropertyAccess(anyRecursionTypes)
    }

    fun isRecursiveSyntheticPropertyAccess(element: KtElement): Boolean {
        if (element !is KtSimpleNameExpression) return false
        return isApplicableSyntheticPropertyAccessPsi(element) && element.isRecursiveSyntheticPropertyAccess()
    }

    fun isApplicablePropertyAccessPsi(element: KtSimpleNameExpression): Boolean {
        val propertyAccessor = element.getParentOfType<KtDeclarationWithBody>(true) as? KtPropertyAccessor ?: return false
        if (element.text != propertyAccessor.property.name) return false
        return element.parent !is KtCallableReferenceExpression
    }

    fun isApplicableSyntheticPropertyAccessPsi(element: KtSimpleNameExpression): Boolean {
        val namedFunction = element.getParentOfType<KtDeclarationWithBody>(true) as? KtNamedFunction ?: return false
        val name = namedFunction.name ?: return false
        val referencedName = element.text.capitalizeAsciiOnly()
        val isGetter = name == "get$referencedName"
        val isSetter = name == "set$referencedName"
        if (!isGetter && !isSetter) return false
        return element.parent !is KtCallableReferenceExpression
    }

    private fun KaSession.hasThisAsReceiver(expression: KtQualifiedExpression) =
        expression.receiverExpression.textMatches(KtTokens.THIS_KEYWORD.value)

    private fun KaSession.hasObjectReceiver(expression: KtQualifiedExpression): Boolean {
        val receiver = expression.receiverExpression as? KtReferenceExpression ?: return false
        val receiverAsClassSymbol = receiver.resolveExpression() as? KaClassSymbol ?: return false
        return receiverAsClassSymbol.classKind.isObject
    }

    private fun KtBinaryExpression?.isAssignmentTo(expression: KtSimpleNameExpression): Boolean =
      this != null && KtPsiUtil.isAssignment(this) && PsiTreeUtil.isAncestor(left, expression, false)

    private fun isSameAccessor(expression: KtSimpleNameExpression, isGetter: Boolean): Boolean {
        val binaryExpr = expression.getStrictParentOfType<KtBinaryExpression>()
        if (isGetter) {
            if (binaryExpr.isAssignmentTo(expression)) {
                return KtTokens.AUGMENTED_ASSIGNMENTS.contains(binaryExpr?.operationToken)
            }
            return true
        } else /* isSetter */ {
            if (binaryExpr.isAssignmentTo(expression)) {
                return true
            }
            val unaryExpr = expression.getStrictParentOfType<KtUnaryExpression>()
            if (unaryExpr?.operationToken.let { it == KtTokens.PLUSPLUS || it == KtTokens.MINUSMINUS }) {
                return true
            }
        }
        return false
    }

    fun KtSimpleNameExpression.isRecursivePropertyAccess(anyRecursionTypes: Boolean): Boolean {
        val propertyAccessor = getParentOfType<KtDeclarationWithBody>(true) as? KtPropertyAccessor ?: return false
      analyze(this) {
        val propertySymbol = resolveToCall()?.successfulVariableAccessCall()?.symbol as? KaPropertySymbol ?: return false
        if (propertySymbol != propertyAccessor.property.symbol) return false

        (parent as? KtQualifiedExpression)?.let {
          val propertyReceiverType = propertySymbol.receiverType
          val receiverType = it.receiverExpression.expressionType?.withNullability(KaTypeNullability.NON_NULLABLE)
          if (anyRecursionTypes) {
            if (receiverType != null && propertyReceiverType != null && !receiverType.isSubtypeOf(propertyReceiverType)) {
              return false
            }
          }
          else {
            if (!hasThisAsReceiver(it) && !hasObjectReceiver(it) && (propertyReceiverType == null || receiverType?.isSubtypeOf(
                propertyReceiverType
              ) == false)
            ) {
              return false
            }
          }
        }
      }
        return isSameAccessor(this, propertyAccessor.isGetter)
    }

    private fun KtSimpleNameExpression.isRecursiveSyntheticPropertyAccess(): Boolean {
        val namedFunction = getParentOfType<KtDeclarationWithBody>(true) as? KtNamedFunction ?: return false

      analyze(this) {
        val syntheticSymbol = mainReference.resolveToSymbol() as? KaSyntheticJavaPropertySymbol ?: return false
        val namedFunctionSymbol = namedFunction.symbol
        if (namedFunctionSymbol != syntheticSymbol.javaGetterSymbol && namedFunctionSymbol != syntheticSymbol.javaSetterSymbol) {
          return false
        }
      }

        val name = namedFunction.name ?: return false
        val referencedName = text.capitalizeAsciiOnly()
        val isGetter = name == "get$referencedName"
        return isSameAccessor(this, isGetter)
    }
}