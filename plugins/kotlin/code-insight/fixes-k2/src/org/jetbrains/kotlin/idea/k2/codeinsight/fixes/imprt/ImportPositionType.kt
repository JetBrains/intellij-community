package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * N.B. This class is heavily copied from [org.jetbrains.kotlin.idea.util.CallType].
 * It is currently used only in an auto-import subsystem, so it has a very narrow name.
 */
internal sealed interface ImportPositionType {
    object Unknown : ImportPositionType
    object DefaultCall : ImportPositionType
    object DotCall : ImportPositionType
    object SafeCall : ImportPositionType
    object SuperMembers : ImportPositionType
    object InfixCall : ImportPositionType
    object OperatorCall : ImportPositionType
    object CallableReference : ImportPositionType
    object ImportDirective : ImportPositionType
    object PackageDirective : ImportPositionType
    object TypeReference : ImportPositionType
    object Annotation : ImportPositionType
    object KDocNameReference : ImportPositionType
}

/**
 * N.B. This class is heavily copied from [org.jetbrains.kotlin.idea.util.CallTypeAndReceiver].
 * It is currently used only in an auto-import subsystem, so it has a very narrow name.
 */
internal sealed class ImportPositionTypeAndReceiver<out TPositionType : ImportPositionType, out TReceiver : KtElement?>(
    val positionType: TPositionType,
    val receiver: TReceiver,
) {
    class Unknown() :
        ImportPositionTypeAndReceiver<ImportPositionType.Unknown, Nothing?>(ImportPositionType.Unknown, null)

    class DefaultCall() :
        ImportPositionTypeAndReceiver<ImportPositionType.DefaultCall, Nothing?>(ImportPositionType.DefaultCall, null)

    class DotCall(receiver: KtExpression) :
        ImportPositionTypeAndReceiver<ImportPositionType.DotCall, KtExpression>(ImportPositionType.DotCall, receiver)

    class SafeCall(receiver: KtExpression) :
        ImportPositionTypeAndReceiver<ImportPositionType.SafeCall, KtExpression>(ImportPositionType.SafeCall, receiver)

    class SuperMembers(receiver: KtSuperExpression) :
        ImportPositionTypeAndReceiver<ImportPositionType.SuperMembers, KtSuperExpression>(ImportPositionType.SuperMembers, receiver)

    class InfixCall(receiver: KtExpression) :
        ImportPositionTypeAndReceiver<ImportPositionType.InfixCall, KtExpression>(ImportPositionType.InfixCall, receiver)

    class OperatorCall(receiver: KtExpression) :
        ImportPositionTypeAndReceiver<ImportPositionType.OperatorCall, KtExpression>(ImportPositionType.OperatorCall, receiver)

    class CallableReference(receiver: KtExpression?) :
        ImportPositionTypeAndReceiver<ImportPositionType.CallableReference, KtExpression?>(ImportPositionType.CallableReference, receiver)

    class ImportDirective(receiver: KtExpression?) :
        ImportPositionTypeAndReceiver<ImportPositionType.ImportDirective, KtExpression?>(ImportPositionType.ImportDirective, receiver)

    class PackageDirective(receiver: KtExpression?) :
        ImportPositionTypeAndReceiver<ImportPositionType.PackageDirective, KtExpression?>(ImportPositionType.PackageDirective, receiver)

    class TypeReference(receiver: KtExpression?) :
        ImportPositionTypeAndReceiver<ImportPositionType.TypeReference, KtExpression?>(ImportPositionType.TypeReference, receiver)

    class Annotation(receiver: KtExpression?) :
        ImportPositionTypeAndReceiver<ImportPositionType.Annotation, KtExpression?>(ImportPositionType.Annotation, receiver)

    /**
     * Important: this position is not detected by [detect] function.
     */
    class KDocNameReference(receiver: KDocName?) :
        ImportPositionTypeAndReceiver<ImportPositionType.KDocNameReference, KDocName?>(ImportPositionType.KDocNameReference, receiver)

    companion object {
        fun detect(expression: KtElement): ImportPositionTypeAndReceiver<*, *> {
            if (expression !is KtSimpleNameExpression) return Unknown()

            val parent = expression.parent
            if (parent is KtCallableReferenceExpression && expression == parent.callableReference) {
                return CallableReference(parent.receiverExpression)
            }

            val receiverExpression = expression.getReceiverExpression()

            if (parent != null) {
                if (expression.isPartOfImportDirectiveExpression()) {
                    return ImportDirective(receiverExpression)
                }

                if (expression.isPartOfPackageDirectiveExpression()) {
                    return PackageDirective(receiverExpression)
                }
                if (parent is KtUserType) {
                    val constructorCallee = (parent.parent as? KtTypeReference)?.parent as? KtConstructorCalleeExpression
                    if (constructorCallee != null && constructorCallee.parent is KtAnnotationEntry) {
                        return Annotation(receiverExpression)
                    }

                    return TypeReference(receiverExpression)
                }
            }

            when (expression) {
                is KtOperationReferenceExpression -> {
                    if (receiverExpression == null) {
                        return Unknown() // incomplete code
                    }
                    return when (parent) {
                        is KtBinaryExpression -> {
                            if (parent.operationToken == KtTokens.IDENTIFIER) InfixCall(receiverExpression)
                            else OperatorCall(receiverExpression)
                        }

                        is KtUnaryExpression -> OperatorCall(receiverExpression)

                        else -> error("Unknown parent for KtOperationReferenceExpression: $parent with text '${parent.text}'")
                    }
                }

                is KtNameReferenceExpression -> {
                    if (receiverExpression == null) {
                        return DefaultCall()
                    }

                    if (receiverExpression is KtSuperExpression) {
                        return SuperMembers(receiverExpression)
                    }

                    return when (parent) {
                        is KtCallExpression -> {
                            if ((parent.parent as KtQualifiedExpression).operationSign == KtTokens.SAFE_ACCESS) SafeCall(
                                receiverExpression
                            )
                            else DotCall(receiverExpression)
                        }

                        is KtQualifiedExpression -> {
                            if (parent.operationSign == KtTokens.SAFE_ACCESS) SafeCall(receiverExpression)
                            else DotCall(receiverExpression)
                        }

                        else -> error("Unknown parent for KtNameReferenceExpression with receiver: $parent")
                    }
                }

                else -> return Unknown()
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
