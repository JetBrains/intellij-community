// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtPossiblyNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasUsages
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isComplexInitializer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class JoinDeclarationAndAssignmentInspection :
    AbstractKotlinApplicableInspectionWithContext<KtProperty, JoinDeclarationAndAssignmentInspection.Context>() {
    data class Context(
        val assignment: KtBinaryExpression,
        val canEraseDeclaredType: Boolean,
        val canOmitDeclaredType: Boolean,
        val movePropertyToConstructorInfo: MovePropertyToConstructorInfo?
    )

    @JvmField
    var reportWithComplexInitializationOfMemberProperties = true

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.checkbox(
            "reportWithComplexInitializationOfMemberProperties",
            KotlinBundle.message("inspection.join.declaration.and.assignment.option.report.with.complex.initialization.of.member.properties")
        )
    )

    override fun getActionFamilyName() = KotlinBundle.message("join.declaration.and.assignment")

    override fun getProblemDescription(element: KtProperty, context: Context) = KotlinBundle.message("can.be.joined.with.assignment")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                visitTargetElement(property, holder, isOnTheFly)
            }
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> = ApplicabilityRanges.SELF

    context(KtAnalysisSession) override fun prepareContext(element: KtProperty): Context? {
        val assignment = findFirstAssignment(element) ?: return null
        val initializer = assignment.right ?: return null

        val initializerType = initializer.getKtType()
        val propertyType = element.typeReference?.getKtType()

        if (initializer.hasReference(element)) return null
        if (initializer.dependsOnNextSiblingsOfProperty(element)) return null

        val isNonLocalVar = element.isVar && !element.isLocal
        val equalTypes = equalNullableTypes(initializerType, propertyType)
        val isSubtype = isSubtype(initializerType, propertyType)
        val typesCanBeMergedSafely = equalTypes || isSubtype

        if (!isNonLocalVar && !typesCanBeMergedSafely) return null

        if (element.isLocal) {
            val isUsedBeforeAssignment = element.nextSiblings().takeWhile { it != assignment }.anyOfHasReference(element)
            if (element.hasModifier(KtTokens.LATEINIT_KEYWORD) && isUsedBeforeAssignment) return null
        } else {
            if (!reportWithComplexInitializationOfMemberProperties) {
                if (initializer.isComplexInitializer()) return null
                if (assignment.nextSiblings().anyOfHasReference(element)) return null
            }
        }

        val hasTypeParameters = !initializerType?.expandedClassSymbol?.typeParameters.isNullOrEmpty()
        val canOmitDeclaredType =
            if (hasTypeParameters) false
            else equalTypes || !element.isVar && isSubtype

        val movePropertyToConstructorInfo =
            if (canBeMovedToConstructor(element, initializer)) MovePropertyToConstructorInfo.create(element, initializer) else null

        return Context(
            assignment = assignment,
            canEraseDeclaredType = assignment.nextSiblings().anyHasSmartCast(element),
            canOmitDeclaredType = canOmitDeclaredType,
            movePropertyToConstructorInfo = movePropertyToConstructorInfo
        )
    }

    context(KtAnalysisSession)
    private fun Sequence<PsiElement>.anyHasSmartCast(element: KtProperty): Boolean {
        val declarationName = element.name ?: return false

        fun PsiElement.hasSmartCast(element: KtProperty): Boolean {
            return anyDescendantOfType<KtNameReferenceExpression> {
                it.text == declarationName && it.reference?.resolve() == element && it.getSmartCastInfo() != null
            }
        }

        return any { it.hasSmartCast(element) }
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        !element.hasDelegate()
                && !element.hasInitializer()
                && element.getter == null
                && element.setter == null
                && element.receiverTypeReference == null
                && element.name != null

    override fun apply(element: KtProperty, context: Context, project: Project, editor: Editor?) {
        if (element.typeReference == null) return

        val assignment = context.assignment
        val initializer = assignment.right ?: return

        element.initializer = initializer
        if (element.hasModifier(KtTokens.LATEINIT_KEYWORD)) element.removeModifier(KtTokens.LATEINIT_KEYWORD)

        val grandParent = (assignment.parent as? KtBlockExpression)?.parent
        val initializerBlock = grandParent as? KtAnonymousInitializer
        val secondaryConstructor = grandParent as? KtSecondaryConstructor

        val newProperty = if (!element.isLocal && (initializerBlock != null || secondaryConstructor != null)) {
            moveComments(from = assignment, to = element)
            assignment.deleteWithPreviousWhitespace()
            if ((initializerBlock?.body as? KtBlockExpression)?.contentRange()?.isEmpty == true) initializerBlock.deleteWithPreviousWhitespace()
            val secondaryConstructorBlock = secondaryConstructor?.bodyBlockExpression
            if (secondaryConstructorBlock?.contentRange()?.isEmpty == true) secondaryConstructorBlock.deleteWithPreviousWhitespace()
            element
        } else {
            moveComments(from = element, to = assignment)
            assignment.replaced(element).also {
                element.deleteWithPreviousWhitespace()
            }
        }

        if (context.movePropertyToConstructorInfo != null) {
            newProperty.moveToConstructor(context.movePropertyToConstructorInfo)
            return
        }

        if (context.canEraseDeclaredType) {
            newProperty.typeReference = null
            editor?.update(newProperty, false)
        } else {
            editor?.update(newProperty, context.canOmitDeclaredType)
        }
    }

    context(KtAnalysisSession)
    private fun canBeMovedToConstructor(element: KtProperty, initializer: KtExpression): Boolean {
        if (element.isLocal) return false
        if (element.getter != null || element.setter != null || element.delegate != null) return false

        val constructor = initializer.mainReference?.resolve()?.parentOfType<KtConstructor<KtPrimaryConstructor>>() ?: return false

        val containingClass = constructor.getContainingClassOrObject()
        if (containingClass.isData()) return false

        val paramSymbol = initializer.mainReference?.resolveToSymbol() as? KtValueParameterSymbol ?: return false
        if (element.nameAsName != paramSymbol.name) return false

        val parameter = paramSymbol.psi as? KtParameter ?: return false
        return !(containingClass.hasModifier(KtTokens.OPEN_KEYWORD)
                && containingClass is KtClass
                && parameter.isUsedInClassInitializer(containingClass)
                )
    }

    private fun KtParameter.isUsedInClassInitializer(containingClass: KtClass): Boolean {
        val classInitializer = containingClass.body?.declarations?.firstIsInstanceOrNull<KtClassInitializer>() ?: return false
        return hasUsages(classInitializer)
    }

    private fun KtElement.deleteWithPreviousWhitespace() {
        val first = prevSibling as? PsiWhiteSpace ?: this
        parent?.deleteChildRange(first, /* last = */ this)
    }

    private fun moveComments(from: KtExpression, to: KtExpression) {
        val psiFactory = KtPsiFactory(from.project)

        val prevComments = from.prevComments()
        val nextComments = from.nextComments()

        if (prevComments.isNotEmpty()) {
            val first = prevComments.first()
            val last = prevComments.last()
            val anchor = to.prevComments().lastOrNull()?.getNextSiblingIgnoringWhitespaceAndComments() ?: to
            anchor.parent.addRangeBefore(first, last, anchor)
            first.parent.deleteChildRange(first, last)
        }

        if (nextComments.isNotEmpty()) {
            val first = nextComments.first()
            val last = nextComments.last()
            val anchor = to.nextComments().lastOrNull() ?: to
            anchor.parent.addRangeAfter(first, last, anchor)
            if (anchor is PsiComment) {
                anchor.parent.addAfter(psiFactory.createNewLine(), anchor)
            }
            first.parent.deleteChildRange(first, last)
        }
    }

    private fun KtExpression.prevComments(): List<PsiElement> {
        fun PsiElement.isComment() = this is PsiComment || this is PsiWhiteSpace
        val comments = allChildren.toList().takeWhile { it.isComment() } +
                prevSiblings().takeWhile { it.isComment() }.toList().reversed().dropLastWhile { it is PsiWhiteSpace }
        return comments.takeIf { it.hasComments() }.orEmpty()
    }

    private fun KtExpression.nextComments(): List<PsiElement> {
        fun PsiElement.isComment() = this is PsiComment || (this is PsiWhiteSpace && !this.textContains('\n'))
        val comments = allChildren.toList().takeLastWhile { it.isComment() } +
                nextSiblings().takeWhile { it.isComment() }.toList().dropLastWhile { it is PsiWhiteSpace }
        return comments.takeIf { it.hasComments() }.orEmpty()
    }

    private fun List<PsiElement>.hasComments(): Boolean = any { it is PsiComment }

    context(KtAnalysisSession)
    private fun findFirstAssignment(property: KtProperty): KtBinaryExpression? {
        if (property.typeReference == null) return null

        val propertyContainer = property.parent as? KtElement ?: return null
        val assignments = propertyContainer.collectAssignments(property)
        if (!assignments.validate(propertyContainer)) return null

        val firstAssignment = assignments.firstOrNull() ?: return null

        if (!property.isLocal
            && firstAssignment.parent != propertyContainer
            && firstAssignment.hasReferenceToSecondaryConstructorParameter()
        ) {
            return null
        }

        val assignmentCall = firstAssignment.left?.resolveCall()?.singleVariableAccessCall()?.symbol ?: return null
        if (assignmentCall != property.getVariableSymbol()) return null

        if (propertyContainer !is KtClassBody) return firstAssignment

        val blockParent = firstAssignment.parent as? KtBlockExpression ?: return null
        return if (blockParent.statements.firstOrNull() == firstAssignment) firstAssignment else null
    }

    private fun KtBinaryExpression.hasReferenceToSecondaryConstructorParameter(): Boolean {
        val secondaryConstructor = getStrictParentOfType<KtSecondaryConstructor>() ?: return false
        return right?.anyDescendantOfType<KtNameReferenceExpression> {
            it.reference?.resolve()?.getStrictParentOfType<KtSecondaryConstructor>() == secondaryConstructor
        } ?: false
    }

    private fun List<KtBinaryExpression>.validate(propertyContainer: KtElement): Boolean {
        fun PsiElement?.isInvalidParent() = when {
            this == null -> true
            this === propertyContainer -> false
            else -> {
                val grandParent = parent
                (grandParent.parent !== propertyContainer) ||
                        (grandParent !is KtAnonymousInitializer && grandParent !is KtSecondaryConstructor)
            }
        }

        if (isEmpty()) return false
        if (any { it.parent.isInvalidParent() }) return false

        val hasOtherAssignmentsInSecondaryConstructors = drop(1).any {
            it.parents.match(KtBlockExpression::class, last = KtSecondaryConstructor::class) != null
        }
        return !hasOtherAssignmentsInSecondaryConstructors
    }

    private fun KtElement.collectAssignments(property: KtProperty): List<KtBinaryExpression> {
        val assignments = mutableListOf<KtBinaryExpression>()

        fun process(binaryExpr: KtBinaryExpression) {
            if (binaryExpr.operationToken != KtTokens.EQ) return
            val leftReference = when (val left = binaryExpr.left) {
                is KtNameReferenceExpression -> left
                is KtDotQualifiedExpression -> left.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return
            if (leftReference.getReferencedName() == property.name) {
                assignments += binaryExpr
            }
        }

        forEachDescendantOfType<KtBinaryExpression>(::process)
        return assignments
    }

    private fun KtElement.dependsOnNextSiblingsOfProperty(property: KtProperty): Boolean {
        val propertyScope = property.parent
        val nextSiblings = property.nextSiblings()
        return anyDescendantOfType<PsiElement> { child ->
            child.resolveAllReferences().any { it != null && PsiTreeUtil.isAncestor(propertyScope, it, false) && it in nextSiblings }
        }
    }

    private fun PsiElement.resolveAllReferences(): Sequence<PsiElement?> =
        PsiReferenceService.getService().getReferences(this, PsiReferenceService.Hints.NO_HINTS)
            .asSequence()
            .map { it.resolve() }

    context(KtAnalysisSession)
    private fun Sequence<PsiElement>.anyOfHasReference(element: KtProperty) = any { it.hasReference(element) }

    context(KtAnalysisSession)
    private fun PsiElement.hasReference(element: KtProperty): Boolean {
        val declarationName = (element.getSymbol() as? KtPossiblyNamedSymbol)?.name.toString()
        return anyDescendantOfType<KtNameReferenceExpression> {
            it.text == declarationName && it.reference?.isReferenceTo(element) ?: false
        }
    }

    context(KtAnalysisSession)
    private fun isSubtype(type: KtType?, superType: KtType?): Boolean {
        if (type == null || superType == null) return false
        return type.isPossiblySubTypeOf(superType)
    }

    private fun PsiElement.prevSiblings(): Sequence<PsiElement> = siblings(forward = false, withItself = false)

    private fun PsiElement.nextSiblings(): Sequence<PsiElement> = siblings(forward = true, withItself = false)

    context(KtAnalysisSession)
    private fun equalNullableTypes(type1: KtType?, type2: KtType?): Boolean {
        if (type1 == null) return type2 == null
        if (type2 == null) return false
        return type1.isEqualTo(type2)
    }

    private fun Editor.update(newProperty: KtProperty, canOmitDeclaredType: Boolean) {
        val newInitializer = newProperty.initializer ?: return
        PsiDocumentManager.getInstance(newProperty.project).doPostponedOperationsAndUnblockDocument(document)

        if (canOmitDeclaredType) {
            val colon = newProperty.colon ?: return
            val typeReference = newProperty.typeReference ?: return
            selectionModel.setSelection(colon.startOffset, typeReference.endOffset)
            moveCaret(typeReference.endOffset, ScrollType.CENTER)
        } else {
            moveCaret(newInitializer.startOffset, ScrollType.CENTER)
        }
    }

    private fun Editor.moveCaret(offset: Int, scrollType: ScrollType) {
        caretModel.moveToOffset(offset)
        scrollingModel.scrollToCaret(scrollType)
    }
}