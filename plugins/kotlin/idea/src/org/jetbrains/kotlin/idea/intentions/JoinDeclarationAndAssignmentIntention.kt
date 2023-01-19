// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.canOmitDeclaredType
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.unblockDocument
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.psi.psiUtil.parents

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
        val assignmentRight = assignment.right ?: return null
        val context = assignment.analyze()
        val propertyDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
        if (assignmentRight.anyDescendantOfType<KtNameReferenceExpression> {
                context[BindingContext.REFERENCE_TARGET, it] == propertyDescriptor
            }) return null
        if (!assignmentRight.let {
                hasNoLocalDependencies(it, element) && ((element.isVar && !element.isLocal) ||
                        equalNullableTypes(it.getType(context), context[BindingContext.TYPE, element.typeReference]))
            }) return null

        return TextRange((element.modifierList ?: element.valOrVarKeyword).startOffset, (element.typeReference ?: element).endOffset)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        if (element.typeReference == null) return
        val assignment = findAssignment(element) ?: return
        val initializer = assignment.right ?: return

        element.initializer = initializer
        if (element.hasModifier(KtTokens.LATEINIT_KEYWORD)) element.removeModifier(KtTokens.LATEINIT_KEYWORD)

        val grandParent = (assignment.parent as? KtBlockExpression)?.parent
        val initializerBlock = grandParent as? KtAnonymousInitializer
        val secondaryConstructor = grandParent as? KtSecondaryConstructor
        val newProperty = if (!element.isLocal && (initializerBlock != null || secondaryConstructor != null)) {
            assignment.delete()
            if ((initializerBlock?.body as? KtBlockExpression)?.isEmpty() == true) initializerBlock.delete()
            val secondaryConstructorBlock = secondaryConstructor?.bodyBlockExpression
            if (secondaryConstructorBlock?.isEmpty() == true) secondaryConstructorBlock.delete()
            element
        } else {
            assignment.replaced(element).also {
                element.delete()
            }
        }
        val newInitializer = newProperty.initializer!!
        val typeReference = newProperty.typeReference!!

        editor?.apply {
            unblockDocument()

            if (newProperty.canOmitDeclaredType(newInitializer, canChangeTypeToSubtype = !newProperty.isVar)) {
                val colon = newProperty.colon!!
                selectionModel.setSelection(colon.startOffset, typeReference.endOffset)
                moveCaret(typeReference.endOffset, ScrollType.CENTER)
            } else {
                moveCaret(newInitializer.startOffset, ScrollType.CENTER)
            }
        }
    }

    private fun findAssignment(property: KtProperty): KtBinaryExpression? {
        val propertyContainer = property.parent as? KtElement ?: return null
        property.typeReference ?: return null

        val assignments = mutableListOf<KtBinaryExpression>()
        fun process(binaryExpr: KtBinaryExpression) {
            if (binaryExpr.operationToken != KtTokens.EQ) return
            val leftReference = when (val left = binaryExpr.left) {
                is KtNameReferenceExpression ->
                    left
                is KtDotQualifiedExpression ->
                    if (left.receiverExpression is KtThisExpression) left.selectorExpression as? KtNameReferenceExpression else null
                else ->
                    null
            } ?: return
            if (leftReference.getReferencedName() != property.name) return
            assignments += binaryExpr
        }
        propertyContainer.forEachDescendantOfType(::process)

        fun PsiElement?.invalidParent(): Boolean {
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

        if (assignments.any { it.parent.invalidParent() }) return null

        val firstAssignment = assignments.firstOrNull() ?: return null
        if (assignments.any {
            it !== firstAssignment && it.parents.match(KtBlockExpression::class, last = KtSecondaryConstructor::class) != null
            }) return null

        val context = firstAssignment.analyze()
        if (!property.isLocal &&
            firstAssignment.parent != propertyContainer &&
            firstAssignment.right?.anyDescendantOfType<KtNameReferenceExpression> {
                val descriptor = context[BindingContext.REFERENCE_TARGET, it]
                descriptor is ValueParameterDescriptor && descriptor.containingDeclaration is ClassConstructorDescriptor
            } == true
        ) return null

        val propertyDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, property] ?: return null
        val assignedDescriptor = firstAssignment.left.getResolvedCall(context)?.candidateDescriptor ?: return null
        if (propertyDescriptor != assignedDescriptor) return null

        if (propertyContainer !is KtClassBody) return firstAssignment

        val blockParent = firstAssignment.parent as? KtBlockExpression ?: return null
        return if (blockParent.statements.firstOrNull() == firstAssignment) firstAssignment else null
    }

    // a block that only contains comments is not empty
    private fun KtBlockExpression.isEmpty() = contentRange().isEmpty

    private fun hasNoLocalDependencies(element: KtElement, property: KtProperty): Boolean {
        val localContext = property.parent
        val nextSiblings = property.siblings(forward = true, withItself = false)
        return !element.anyDescendantOfType<PsiElement> { child ->
            child.resolveAllReferences().any { it != null && PsiTreeUtil.isAncestor(localContext, it, false) && it in nextSiblings }
        }
    }
}

private fun PsiElement.resolveAllReferences(): Sequence<PsiElement?> =
    PsiReferenceService.getService().getReferences(this, PsiReferenceService.Hints.NO_HINTS)
        .asSequence()
        .map { it.resolve() }
