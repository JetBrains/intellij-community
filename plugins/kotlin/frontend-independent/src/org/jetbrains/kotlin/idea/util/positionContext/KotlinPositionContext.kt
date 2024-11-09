// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util.positionContext

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

sealed class KotlinRawPositionContext {
    abstract val position: PsiElement
}

class KotlinClassifierNamePositionContext(
    override val position: PsiElement,
    val classLikeDeclaration: KtClassLikeDeclaration,
) : KotlinRawPositionContext()

sealed class KotlinValueParameterPositionContext : KotlinRawPositionContext() {
    abstract val ktParameter: KtParameter
}

class KotlinSimpleParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : KotlinValueParameterPositionContext()

/**
 * Primary constructor parameters can override properties from superclasses, so they should be analyzed
 * in dependent analysis session, which is why a separate position context is required.
 */
class KotlinPrimaryConstructorParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : KotlinValueParameterPositionContext()

class KotlinIncorrectPositionContext(
    override val position: PsiElement
) : KotlinRawPositionContext()

class KotlinTypeConstraintNameInWhereClausePositionContext(
    override val position: PsiElement,
    val typeParametersOwner: KtTypeParameterListOwner
) : KotlinRawPositionContext()

sealed class KotlinNameReferencePositionContext : KotlinRawPositionContext() {
    abstract val reference: KtReference
    abstract val nameExpression: KtElement
    abstract val explicitReceiver: KtElement?

    abstract val name: Name
}

sealed class KotlinSimpleNameReferencePositionContext : KotlinNameReferencePositionContext() {
    abstract override val reference: KtSimpleNameReference
    abstract override val nameExpression: KtSimpleNameExpression
    abstract override val explicitReceiver: KtExpression?

    override val name: Name
        get() = nameExpression.getReferencedNameAsName()
}

class KotlinImportDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()

class KotlinPackageDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()


class KotlinTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val typeReference: KtTypeReference?,
) : KotlinSimpleNameReferencePositionContext()

class KotlinAnnotationTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val annotationEntry: KtAnnotationEntry,
) : KotlinSimpleNameReferencePositionContext()

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
class KotlinSuperTypeCallNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val superExpression: KtSuperExpression,
) : KotlinSimpleNameReferencePositionContext()

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
class KotlinSuperReceiverNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtSuperExpression,
) : KotlinSimpleNameReferencePositionContext()

class KotlinExpressionNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()

class KotlinInfixCallPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()


class KotlinWithSubjectEntryPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val subjectExpression: KtExpression,
    val whenCondition: KtWhenCondition,
) : KotlinSimpleNameReferencePositionContext()

class KotlinCallableReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()

/**
 * Position in label reference expression, e.g.:
 * * `break@r<caret>ef`
 * * `continue@r<caret>ef`
 * * `this@r<caret>ef`
 * * `super@r<caret>ef`
 * * `return@r<caret>ef`
 */
class KotlinLabelReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtLabelReferenceExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()

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
class KotlinMemberDeclarationExpectedPositionContext(
    override val position: PsiElement,
    val classBody: KtClassBody
) : KotlinRawPositionContext()

sealed class KDocNameReferencePositionContext : KotlinNameReferencePositionContext() {
    abstract override val reference: KDocReference
    abstract override val nameExpression: KDocName
    abstract override val explicitReceiver: KDocName?

    override val name: Name
        get() = nameExpression.getQualifiedNameAsFqName().shortName()
}

class KDocParameterNamePositionContext(
    override val position: PsiElement,
    override val reference: KDocReference,
    override val nameExpression: KDocName,
    override val explicitReceiver: KDocName?,
) : KDocNameReferencePositionContext()

class KDocLinkNamePositionContext(
    override val position: PsiElement,
    override val reference: KDocReference,
    override val nameExpression: KDocName,
    override val explicitReceiver: KDocName?,
) : KDocNameReferencePositionContext()

class KotlinUnknownPositionContext(
    override val position: PsiElement
) : KotlinRawPositionContext()

object KotlinPositionContextDetector {
    fun detect(position: PsiElement): KotlinRawPositionContext {
        return detectForPositionWithSimpleNameReference(position)
            ?: detectForPositionWithKDocReference(position)
            ?: detectForPositionWithoutReference(position)
            ?: KotlinUnknownPositionContext(position)
    }

    private fun detectForPositionWithoutReference(position: PsiElement): KotlinRawPositionContext? {
        val parent = position.parent ?: return null
        val grandparent = parent.parent
        return when {
            parent is KtClassLikeDeclaration && parent.nameIdentifier == position -> {
                KotlinClassifierNamePositionContext(position, parent)
            }

            parent is KtParameter -> {
                if (parent.ownerFunction is KtPrimaryConstructor) {
                    KotlinPrimaryConstructorParameterPositionContext(position, parent)
                } else {
                    KotlinSimpleParameterPositionContext(position, parent)
                }
            }

            parent is PsiErrorElement && grandparent is KtClassBody -> {
                KotlinMemberDeclarationExpectedPositionContext(position, grandparent)
            }

            else -> null
        }
    }

    private fun detectForPositionWithSimpleNameReference(position: PsiElement): KotlinRawPositionContext? {
        val reference = (position.parent as? KtSimpleNameExpression)?.mainReference
            ?: return null
        val nameExpression = reference.expression
        val explicitReceiver = nameExpression.getReceiverExpression()
        val parent = nameExpression.parent
        val subjectExpressionForWhenCondition = (parent as? KtWhenCondition)?.getSubjectExpression()

        return when {
            parent is KtUserType -> {
                detectForTypeContext(parent, position, reference, nameExpression, explicitReceiver)
            }

            parent is KtCallableReferenceExpression -> {
                KotlinCallableReferencePositionContext(
                    position, reference, nameExpression, parent.receiverExpression
                )
            }

            parent is KtWhenCondition && subjectExpressionForWhenCondition != null -> {
                KotlinWithSubjectEntryPositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                    subjectExpressionForWhenCondition,
                    whenCondition = parent,
                )
            }

            nameExpression.isReferenceExpressionInImportDirective() -> {
                KotlinImportDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }

            nameExpression.isNameExpressionInsidePackageDirective() -> {
                KotlinPackageDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }

            parent is KtTypeConstraint -> KotlinTypeConstraintNameInWhereClausePositionContext(
                position,
                position.parentOfType()!!,
            )

            parent is KtBinaryExpression && parent.operationReference == nameExpression -> {
                KotlinInfixCallPositionContext(
                    position, reference, nameExpression, explicitReceiver
                )
            }

            explicitReceiver is KtSuperExpression -> KotlinSuperReceiverNameReferencePositionContext(
                position = position,
                reference = reference,
                nameExpression = nameExpression,
                explicitReceiver = explicitReceiver,
            )

            nameExpression is KtLabelReferenceExpression -> {
                KotlinLabelReferencePositionContext(position, reference, nameExpression, explicitReceiver)
            }

            else -> {
                KotlinExpressionNameReferencePositionContext(position, reference, nameExpression, explicitReceiver)
            }
        }
    }

    private fun detectForPositionWithKDocReference(position: PsiElement): KotlinNameReferencePositionContext? {
        val kDocName = position.getStrictParentOfType<KDocName>() ?: return null
        val kDocLink = kDocName.getStrictParentOfType<KDocLink>() ?: return null
        val kDocReference = kDocName.mainReference
        val kDocNameQualifier = kDocName.getQualifier()

        return when (kDocLink.getTagIfSubject()?.knownTag) {
            KDocKnownTag.PARAM -> KDocParameterNamePositionContext(position, kDocReference, kDocName, kDocNameQualifier)
            else -> KDocLinkNamePositionContext(position, kDocReference, kDocName, kDocNameQualifier)
        }
    }

    private fun KtWhenCondition.getSubjectExpression(): KtExpression? {
        val whenEntry = (parent as? KtWhenEntry) ?: return null
        val whenExpression = whenEntry.parent as? KtWhenExpression ?: return null
        return whenExpression.subjectExpression
    }

    private tailrec fun KtExpression.isReferenceExpressionInImportDirective(): Boolean = when (val parent = parent) {
        is KtImportDirective -> parent.importedReference == this
        is KtDotQualifiedExpression -> parent.isReferenceExpressionInImportDirective()

        else -> false
    }

    private tailrec fun KtExpression.isNameExpressionInsidePackageDirective(): Boolean = when (val parent = parent) {
        is KtPackageDirective -> parent.packageNameExpression == this
        is KtDotQualifiedExpression -> parent.isNameExpressionInsidePackageDirective()

        else -> false
    }

    private fun detectForTypeContext(
        userType: KtUserType,
        position: PsiElement,
        reference: KtSimpleNameReference,
        nameExpression: KtSimpleNameExpression,
        explicitReceiver: KtExpression?
    ): KotlinRawPositionContext {
        val typeReference = (userType.parent as? KtTypeReference)?.takeIf { it.typeElement == userType }
        val typeReferenceOwner = typeReference?.parent
        return when {
            typeReferenceOwner is KtConstructorCalleeExpression -> {
                val constructorCall = typeReferenceOwner.takeIf { it.typeReference == typeReference }
                val annotationEntry = (constructorCall?.parent as? KtAnnotationEntry)?.takeIf { it.calleeExpression == constructorCall }
                annotationEntry?.let {
                    KotlinAnnotationTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }

            typeReferenceOwner is KtSuperExpression -> {
                val superTypeCallEntry = typeReferenceOwner.takeIf { it.superTypeQualifier == typeReference }
                superTypeCallEntry?.let {
                    KotlinSuperTypeCallNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }

            typeReferenceOwner is KtTypeConstraint && typeReferenceOwner.children.any { it is PsiErrorElement } -> {
                KotlinIncorrectPositionContext(position)
            }

            else -> null
        } ?: KotlinTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, typeReference)
    }
}