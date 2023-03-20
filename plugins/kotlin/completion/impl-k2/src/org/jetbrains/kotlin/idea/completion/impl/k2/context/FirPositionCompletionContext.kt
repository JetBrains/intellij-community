// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.context

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeInDependedAnalysisSession
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

internal sealed class FirRawPositionCompletionContext {
    abstract val position: PsiElement
}

internal class FirClassifierNamePositionContext(
    override val position: PsiElement,
    val classLikeDeclaration: KtClassLikeDeclaration,
) : FirRawPositionCompletionContext()

internal sealed class FirValueParameterPositionContext : FirRawPositionCompletionContext() {
    abstract val ktParameter: KtParameter
}

internal class FirSimpleParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : FirValueParameterPositionContext()

/**
 * Primary constructor parameters can override properties from superclasses, so they should be analyzed with
 * [analyzeInDependedAnalysisSession], which is why a separate position context is required.
 */
internal class FirPrimaryConstructorParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : FirValueParameterPositionContext()

internal class FirIncorrectPositionContext(
    override val position: PsiElement
) : FirRawPositionCompletionContext()

internal class FirTypeConstraintNameInWhereClausePositionContext(
    override val position: PsiElement,
    val typeParametersOwner: KtTypeParameterListOwner
) : FirRawPositionCompletionContext()

internal sealed class FirNameReferencePositionContext : FirRawPositionCompletionContext() {
    abstract val reference: KtSimpleNameReference
    abstract val nameExpression: KtSimpleNameExpression
    abstract val explicitReceiver: KtExpression?
}

internal class FirImportDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : FirNameReferencePositionContext()

internal class FirPackageDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : FirNameReferencePositionContext()


internal class FirTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val typeReference: KtTypeReference?,
) : FirNameReferencePositionContext()

internal class FirAnnotationTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val annotationEntry: KtAnnotationEntry,
) : FirNameReferencePositionContext()

/**
 * Example
 * ```
 * class A {
 *   fun test() {
 *     super<<caret>>
 *   }
 * }
 * ```
 */
internal class FirSuperTypeCallNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val superExpression: KtSuperExpression,
) : FirNameReferencePositionContext()

/**
 * Example
 * ```
 * class A {
 *   fun test() {
 *     super.<caret>
 *   }
 * }
 * ```
 */
internal class FirSuperReceiverNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val superExpression: KtSuperExpression,
) : FirNameReferencePositionContext()

internal class FirExpressionNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : FirNameReferencePositionContext()

internal class FirInfixCallPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : FirNameReferencePositionContext()


internal class FirWithSubjectEntryPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val whenCondition: KtWhenCondition,
) : FirNameReferencePositionContext()

internal class FirCallableReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : FirNameReferencePositionContext()

/**
 * Position in class body, on which member declaration or class initializer is expected
 *
 * Examples
 * ```
 * class A {
 *   f<caret>
 * }
 *
 * class B {
 *   private <caret>
 * }
 * ```
 */
internal class FirMemberDeclarationExpectedPositionContext(
    override val position: PsiElement,
    val classBody: KtClassBody
) : FirRawPositionCompletionContext()

internal class FirUnknownPositionContext(
    override val position: PsiElement
) : FirRawPositionCompletionContext()

internal object FirPositionCompletionContextDetector {
    fun detect(basicContext: FirBasicCompletionContext): FirRawPositionCompletionContext {
        val position = basicContext.parameters.position
        return detectForPositionWithReference(position)
            ?: detectForPositionWithoutReference(position)
            ?: FirUnknownPositionContext(position)
    }

    private fun detectForPositionWithoutReference(position: PsiElement): FirRawPositionCompletionContext? {
        val parent = position.parent ?: return null
        val grandparent = parent.parent
        return when {
            parent is KtClassLikeDeclaration && parent.nameIdentifier == position -> {
                FirClassifierNamePositionContext(position, parent)
            }

            parent is KtParameter -> {
                if (parent.ownerFunction is KtPrimaryConstructor) {
                    FirPrimaryConstructorParameterPositionContext(position, parent)
                } else {
                    FirSimpleParameterPositionContext(position, parent)
                }
            }

            parent is PsiErrorElement && grandparent is KtClassBody -> {
                FirMemberDeclarationExpectedPositionContext(position, grandparent)
            }

            else -> null
        }
    }

    private fun detectForPositionWithReference(position: PsiElement): FirRawPositionCompletionContext? {
        val reference = (position.parent as? KtSimpleNameExpression)?.mainReference
            ?: return null
        val nameExpression = reference.expression.takeIf { it !is KtLabelReferenceExpression }
            ?: return null
        val explicitReceiver = nameExpression.getReceiverExpression()
        val parent = nameExpression.parent

        return when {
            parent is KtUserType -> {
                detectForTypeContext(parent, position, reference, nameExpression, explicitReceiver)
            }
            parent is KtCallableReferenceExpression -> {
                FirCallableReferencePositionContext(
                    position, reference, nameExpression, parent.receiverExpression
                )
            }
            parent is KtWhenCondition && parent.isConditionOnWhenWithSubject() -> {
                FirWithSubjectEntryPositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver, parent
                )
            }
            nameExpression.isReferenceExpressionInImportDirective() -> {
                FirImportDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }

            nameExpression.isImportExpressionInsidePackageDirective() -> {
                FirPackageDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }
            parent is KtTypeConstraint -> FirTypeConstraintNameInWhereClausePositionContext(
                position,
                position.parentOfType()!!,
            )
            parent is KtBinaryExpression && parent.operationReference == nameExpression -> {
                FirInfixCallPositionContext(
                    position, reference, nameExpression, explicitReceiver
                )
            }
            explicitReceiver is KtSuperExpression -> FirSuperReceiverNameReferencePositionContext(
                position,
                reference,
                nameExpression,
                explicitReceiver,
                explicitReceiver
            )
            else -> {
                FirExpressionNameReferencePositionContext(position, reference, nameExpression, explicitReceiver)
            }
        }
    }

    private fun KtWhenCondition.isConditionOnWhenWithSubject(): Boolean {
        val whenEntry = (parent as? KtWhenEntry) ?: return false
        val whenExpression = whenEntry.parent as? KtWhenExpression ?: return false
        return whenExpression.subjectExpression != null
    }

    private fun KtExpression.isReferenceExpressionInImportDirective() = when (val parent = parent) {
        is KtImportDirective -> parent.importedReference == this
        is KtDotQualifiedExpression -> {
            val importDirective = parent.parent as? KtImportDirective
            importDirective?.importedReference == parent
        }
        else -> false
    }

    private fun KtExpression.isImportExpressionInsidePackageDirective() = when (val parent = parent) {
        is KtPackageDirective -> parent.packageNameExpression == this
        is KtDotQualifiedExpression -> {
            val packageDirective = parent.parent as? KtPackageDirective
            packageDirective?.packageNameExpression == parent
        }
        else -> false
    }

    private fun detectForTypeContext(
        userType: KtUserType,
        position: PsiElement,
        reference: KtSimpleNameReference,
        nameExpression: KtSimpleNameExpression,
        explicitReceiver: KtExpression?
    ): FirRawPositionCompletionContext {
        val typeReference = (userType.parent as? KtTypeReference)?.takeIf { it.typeElement == userType }
        val typeReferenceOwner = typeReference?.parent
        return when {
            typeReferenceOwner is KtConstructorCalleeExpression -> {
                val constructorCall = typeReferenceOwner.takeIf { it.typeReference == typeReference }
                val annotationEntry = (constructorCall?.parent as? KtAnnotationEntry)?.takeIf { it.calleeExpression == constructorCall }
                annotationEntry?.let {
                    FirAnnotationTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }
            typeReferenceOwner is KtSuperExpression -> {
                val superTypeCallEntry = typeReferenceOwner.takeIf { it.superTypeQualifier == typeReference }
                superTypeCallEntry?.let {
                    FirSuperTypeCallNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }
            typeReferenceOwner is KtTypeConstraint && typeReferenceOwner.children.any { it is PsiErrorElement } -> {
                FirIncorrectPositionContext(position)
            }
            else -> null
        } ?: FirTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, typeReference)
    }

    inline fun analyzeInContext(
        basicContext: FirBasicCompletionContext,
        positionContext: FirRawPositionCompletionContext,
        action: KtAnalysisSession.() -> Unit
    ) {
        return when (positionContext) {
            is FirUnknownPositionContext,
            is FirImportDirectivePositionContext,
            is FirPackageDirectivePositionContext,
            is FirTypeConstraintNameInWhereClausePositionContext,
            is FirIncorrectPositionContext,
            is FirSimpleParameterPositionContext,
            is FirClassifierNamePositionContext -> {
                analyze(basicContext.originalKtFile, action = action)
            }

            is FirNameReferencePositionContext -> analyzeInDependedAnalysisSession(
                basicContext.originalKtFile,
                positionContext.nameExpression,
                action = action
            )

            is FirMemberDeclarationExpectedPositionContext -> analyzeInDependedAnalysisSession(
                basicContext.originalKtFile,
                positionContext.classBody,
                action = action
            )

            is FirPrimaryConstructorParameterPositionContext -> analyzeInDependedAnalysisSession(
                basicContext.originalKtFile,
                positionContext.ktParameter,
                action = action
            )
        }
    }
}