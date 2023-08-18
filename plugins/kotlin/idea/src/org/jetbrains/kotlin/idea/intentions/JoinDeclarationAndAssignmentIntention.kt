// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.canOmitDeclaredType
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.unblockDocument
import org.jetbrains.kotlin.idea.inspections.CanBePrimaryConstructorPropertyUtils.canBePrimaryConstructorProperty
import org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.LATEINIT_KEYWORD
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.util.match

@Suppress("DEPRECATION")
class JoinDeclarationAndAssignmentInspection : IntentionBasedInspection<KtProperty>(
    JoinDeclarationAndAssignmentIntention::class,
    KotlinBundle.message("can.be.joined.with.assignment")
)

class JoinDeclarationAndAssignmentIntention : SelfTargetingRangeIntention<KtProperty>(
    KtProperty::class.java,
    KotlinBundle.lazyMessage("join.declaration.and.assignment")
) {

    private fun equalNullableTypes(type1: KotlinType?, type2: KotlinType?): Boolean {
        if (type1 == null) return type2 == null
        if (type2 == null) return false
        return TypeUtils.equalTypes(type1, type2)
    }

    override fun applicabilityRange(element: KtProperty): TextRange? {
        if (element.hasDelegate()
            || element.hasInitializer()
            || element.setter != null
            || element.getter != null
            || element.receiverTypeReference != null
            || element.name == null
        ) {
            return null
        }

        val assignment = findAssignment(element) ?: return null
        val initializer = assignment.right ?: return null
        val context = assignment.analyze()
        val propertyDescriptor = element.descriptor(context) ?: return null

        if (initializer.hasReference(propertyDescriptor, context)) return null
        if (initializer.dependsOnNextSiblingsOfProperty(element)) return null

        val isNonLocalVar = element.isVar && !element.isLocal
        val typesCanBeMergedSafely = lazy {
            val initializerType = initializer.getType(context)
            val propertyType = context[BindingContext.TYPE, element.typeReference]
            when {
                equalNullableTypes(initializerType, propertyType) -> true
                isSubtype(initializerType, propertyType) -> !assignment.nextSiblings().hasSmartCast(propertyDescriptor, context)
                else -> false
            }
        }

        if (!isNonLocalVar && !typesCanBeMergedSafely.value) return null

        val isUsedBeforeAssignment = lazy {
            element.nextSiblings().takeWhile { it != assignment }.hasReference(propertyDescriptor, context)
        }

        if (element.isLocal && element.hasModifier(LATEINIT_KEYWORD) && isUsedBeforeAssignment.value) return null

        val startOffset = (element.modifierList ?: element.valOrVarKeyword).startOffset
        val endOffset = (element.typeReference ?: element).endOffset
        return TextRange(startOffset, endOffset)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        if (element.typeReference == null) return
        val assignment = findAssignment(element) ?: return
        val initializer = assignment.right ?: return

        element.initializer = initializer
        if (element.hasModifier(LATEINIT_KEYWORD)) element.removeModifier(LATEINIT_KEYWORD)

        val grandParent = (assignment.parent as? KtBlockExpression)?.parent
        val initializerBlock = grandParent as? KtAnonymousInitializer
        val secondaryConstructor = grandParent as? KtSecondaryConstructor
        val newProperty = if (!element.isLocal && (initializerBlock != null || secondaryConstructor != null)) {
            assignment.deleteWithPreviousWhitespace()
            if ((initializerBlock?.body as? KtBlockExpression)?.isEmpty() == true) initializerBlock.deleteWithPreviousWhitespace()
            val secondaryConstructorBlock = secondaryConstructor?.bodyBlockExpression
            if (secondaryConstructorBlock?.isEmpty() == true) secondaryConstructorBlock.deleteWithPreviousWhitespace()
            element
        } else {
            assignment.replaced(element).also {
                element.deleteWithPreviousWhitespace()
            }
        }
        val newInitializer = newProperty.initializer!!
        val typeReference = newProperty.typeReference!!

        editor?.apply {
            unblockDocument()

            when {
                newProperty.canBePrimaryConstructorProperty() != null -> newProperty.moveToConstructor()

                newProperty.canOmitDeclaredType(newInitializer, canChangeTypeToSubtype = !newProperty.isVar) -> {
                    val colon = newProperty.colon!!
                    selectionModel.setSelection(colon.startOffset, typeReference.endOffset)
                    moveCaret(typeReference.endOffset, ScrollType.CENTER)
                }

                else -> {
                    moveCaret(newInitializer.startOffset, ScrollType.CENTER)
                }
            }
        }
    }

    private fun findAssignment(property: KtProperty): KtBinaryExpression? {
        val propertyContainer = property.parent as? KtElement ?: return null
        if (property.typeReference == null) return null

        val assignments = mutableListOf<KtBinaryExpression>()

        fun process(binaryExpr: KtBinaryExpression) {
            if (binaryExpr.operationToken != KtTokens.EQ) return
            val leftReference = when (val left = binaryExpr.left) {
                is KtNameReferenceExpression -> left
                is KtDotQualifiedExpression -> left.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return
            if (leftReference.getReferencedName() != property.name) return
            assignments += binaryExpr
        }

        propertyContainer.forEachDescendantOfType<KtBinaryExpression>(::process)

        fun PsiElement?.isInvalidParent(): Boolean {
            when {
                this == null -> return true
                this === propertyContainer -> return false
                else -> {
                    val grandParent = parent
                    if (grandParent.parent !== propertyContainer) return true
                    return grandParent !is KtAnonymousInitializer && grandParent !is KtSecondaryConstructor
                }
            }
        }

        if (assignments.any { it.parent.isInvalidParent() }) return null

        val firstAssignment = assignments.firstOrNull() ?: return null
        val hasOtherAssignmentsInSecondaryConstructors = assignments.drop(1).any {
            it.parents.match(KtBlockExpression::class, last = KtSecondaryConstructor::class) != null
        }
        if (hasOtherAssignmentsInSecondaryConstructors) return null

        val context = firstAssignment.analyze()

        if (!property.isLocal && firstAssignment.parent != propertyContainer) {
            val secondaryConstructor = firstAssignment.getStrictParentOfType<KtSecondaryConstructor>()?.descriptor(context)
            if (secondaryConstructor != null) {
                val isAssignedConstructorParameter = firstAssignment.right?.anyDescendantOfType<KtNameReferenceExpression> {
                    (it.descriptor(context) as? ValueParameterDescriptor)?.containingDeclaration == secondaryConstructor
                } == true
                if (isAssignedConstructorParameter) return null
            }
        }

        val propertyDescriptor = property.descriptor(context) ?: return null
        val assignedDescriptor = firstAssignment.left.getResolvedCall(context)?.candidateDescriptor ?: return null
        if (propertyDescriptor != assignedDescriptor) return null

        if (propertyContainer !is KtClassBody) return firstAssignment

        val blockParent = firstAssignment.parent as? KtBlockExpression ?: return null
        return if (blockParent.statements.firstOrNull() == firstAssignment) firstAssignment else null
    }

    // a block that only contains comments is not empty
    private fun KtBlockExpression.isEmpty() = contentRange().isEmpty

    private fun KtElement.dependsOnNextSiblingsOfProperty(property: KtProperty): Boolean {
        val propertyScope = property.parent
        val nextSiblings = property.nextSiblings()
        return anyDescendantOfType<PsiElement> { child ->
            child.resolveAllReferences().any { it != null && PsiTreeUtil.isAncestor(propertyScope, it, false) && it in nextSiblings }
        }
    }

    private fun PsiElement.hasReference(declaration: DeclarationDescriptor, context: BindingContext): Boolean {
        val declarationName = declaration.name.asString()
        return anyDescendantOfType<KtNameReferenceExpression> {
            it.text == declarationName && it.descriptor(context) == declaration
        }
    }

    private fun PsiElement.hasSmartCast(declaration: DeclarationDescriptor, context: BindingContext): Boolean {
        val declarationName = declaration.name.asString()
        return anyDescendantOfType<KtNameReferenceExpression> {
            it.text == declarationName && it.descriptor(context) == declaration &&
                    context[BindingContext.SMARTCAST, it] != null
        }
    }

    private fun Sequence<PsiElement>.hasReference(declaration: DeclarationDescriptor, context: BindingContext): Boolean =
        any { it.hasReference(declaration, context) }

    private fun Sequence<PsiElement>.hasSmartCast(declaration: DeclarationDescriptor, context: BindingContext): Boolean =
        any { it.hasSmartCast(declaration, context) }

    private fun isSubtype(type: KotlinType?, superType: KotlinType?): Boolean {
        if (type == null || superType == null) return false
        return type.isSubtypeOf(superType)
    }

    private fun PsiElement.nextSiblings(): Sequence<PsiElement> = siblings(forward = true, withItself = false)

    private fun KtDeclaration.descriptor(context: BindingContext): DeclarationDescriptor? =
        context[BindingContext.DECLARATION_TO_DESCRIPTOR, this]

    private fun KtNameReferenceExpression.descriptor(context: BindingContext): DeclarationDescriptor? =
        context[BindingContext.REFERENCE_TARGET, this]
}

private fun KtElement.deleteWithPreviousWhitespace() {
    val first = prevSibling as? PsiWhiteSpace ?: this
    parent?.deleteChildRange(first, /* last = */ this)
}

private fun PsiElement.resolveAllReferences(): Sequence<PsiElement?> =
    PsiReferenceService.getService().getReferences(this, PsiReferenceService.Hints.NO_HINTS)
        .asSequence()
        .map { it.resolve() }
