package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * N.B. This class is heavily copied from [org.jetbrains.kotlin.idea.util.CallTypeAndReceiver].
 * It is currently used only in an auto-import subsystem, so it has a very narrow name.
 */
internal sealed class ImportPositionContext<out TPosition : KtElement, out TReceiver : KtElement?>(
    val position: TPosition,
    val receiver: TReceiver,
) {
    class Unknown(position: KtElement) :
        ImportPositionContext<KtElement, Nothing?>(position, null)

    class DefaultCall(position: KtSimpleNameExpression) :
        ImportPositionContext<KtSimpleNameExpression, Nothing?>(position, null)

    class DotCall(position: KtSimpleNameExpression, receiver: KtExpression) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression>(position, receiver)

    class SafeCall(position: KtSimpleNameExpression, receiver: KtExpression) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression>(position, receiver)

    class SuperMembers(position: KtSimpleNameExpression, receiver: KtSuperExpression) :
        ImportPositionContext<KtSimpleNameExpression, KtSuperExpression>(position, receiver)

    class InfixCall(position: KtOperationReferenceExpression, receiver: KtExpression) :
        ImportPositionContext<KtOperationReferenceExpression, KtExpression>(position, receiver)

    class OperatorCall(position: KtReferenceExpression, receiver: KtExpression) :
        ImportPositionContext<KtReferenceExpression, KtExpression>(position, receiver)

    class CallableReference(position: KtSimpleNameExpression, receiver: KtExpression?) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression?>(position, receiver)

    class ImportDirective(position: KtSimpleNameExpression, receiver: KtExpression?) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression?>(position, receiver)

    class PackageDirective(position: KtSimpleNameExpression, receiver: KtExpression?) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression?>(position, receiver)

    class TypeReference(position: KtSimpleNameExpression, receiver: KtExpression?) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression?>(position, receiver)

    /**
     * Important: this position is not detected by [detect] function.
     */
    class Delegate(position: KtExpression, receiver: KtExpression?) :
        ImportPositionContext<KtExpression, KtExpression?>(position, receiver)

    class Annotation(position: KtSimpleNameExpression, receiver: KtExpression?) :
        ImportPositionContext<KtSimpleNameExpression, KtExpression?>(position, receiver)

    /**
     * Important: this position is not detected by [detect] function.
     */
    class KDocNameReference(position: KDocName, receiver: KDocName?) :
        ImportPositionContext<KDocName, KDocName?>(position, receiver)

    companion object {
        fun detect(expression: KtElement): ImportPositionContext<*, *> {
            if (expression !is KtSimpleNameExpression) return Unknown(expression)

            val parent = expression.parent
            if (parent is KtCallableReferenceExpression && expression == parent.callableReference) {
                return CallableReference(expression, parent.receiverExpression)
            }

            val receiverExpression = expression.getReceiverExpression()

            if (parent != null) {
                if (expression.isPartOfImportDirectiveExpression()) {
                    return ImportDirective(expression, receiverExpression)
                }

                if (expression.isPartOfPackageDirectiveExpression()) {
                    return PackageDirective(expression, receiverExpression)
                }
                if (parent is KtUserType) {
                    val constructorCallee = (parent.parent as? KtTypeReference)?.parent as? KtConstructorCalleeExpression
                    if (constructorCallee != null && constructorCallee.parent is KtAnnotationEntry) {
                        return Annotation(expression, receiverExpression)
                    }

                    return TypeReference(expression, receiverExpression)
                }
            }

            when (expression) {
                is KtOperationReferenceExpression -> {
                    if (receiverExpression == null) {
                        return Unknown(expression) // incomplete code
                    }
                    return when (parent) {
                        is KtBinaryExpression -> {
                            if (parent.operationToken == KtTokens.IDENTIFIER) InfixCall(expression, receiverExpression)
                            else OperatorCall(expression, receiverExpression)
                        }

                        is KtUnaryExpression -> OperatorCall(expression, receiverExpression)

                        else -> error("Unknown parent for KtOperationReferenceExpression: $parent with text '${parent.text}'")
                    }
                }

                is KtNameReferenceExpression -> {
                    if (receiverExpression == null) {
                        return DefaultCall(expression)
                    }

                    if (receiverExpression is KtSuperExpression) {
                        return SuperMembers(expression, receiverExpression)
                    }

                    return when (parent) {
                        is KtCallExpression -> {
                            if ((parent.parent as KtQualifiedExpression).operationSign == KtTokens.SAFE_ACCESS) SafeCall(
                                expression,
                                receiverExpression
                            )
                            else DotCall(expression, receiverExpression)
                        }

                        is KtQualifiedExpression -> {
                            if (parent.operationSign == KtTokens.SAFE_ACCESS) SafeCall(expression, receiverExpression)
                            else DotCall(expression, receiverExpression)
                        }

                        else -> error("Unknown parent for KtNameReferenceExpression with receiver: $parent")
                    }
                }

                else -> return Unknown(expression)
            }
        }
    }
}

/**
 * Performs a more correct check compared to [org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression] -
 * traverses through all parent [KtDotQualifiedExpression]s.
 */
private fun KtSimpleNameExpression.isPartOfImportDirectiveExpression(): Boolean {
    val firstNonQualifiedParent = parents
        .dropWhile { it is KtDotQualifiedExpression }
        .firstOrNull()

    return firstNonQualifiedParent is KtImportDirective
}

/**
 * Performs a more correct check compared to [org.jetbrains.kotlin.psi.psiUtil.isPackageDirectiveExpression] -
 * traverses through all parent [KtDotQualifiedExpression]s.
 */
private fun KtSimpleNameExpression.isPartOfPackageDirectiveExpression(): Boolean {
    val firstNonQualifiedParent = parents
        .dropWhile { it is KtDotQualifiedExpression }
        .firstOrNull()

    return firstNonQualifiedParent is KtPackageDirective
}
