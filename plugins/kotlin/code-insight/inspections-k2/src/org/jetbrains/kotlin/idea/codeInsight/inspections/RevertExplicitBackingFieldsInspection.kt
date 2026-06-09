// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.propertyVisitor
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class RevertExplicitBackingFieldsInspection :
    KotlinApplicableInspectionBase.Simple<KtProperty, RevertExplicitBackingFieldsInspection.Context>() {

    data class Context(
        val references: List<SmartPsiElementPointer<KtNameReferenceExpression>>,
        val backingFieldInitializerText: String?,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtProperty,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replace.explicit.backing.field.with.private.property")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        val backingField = element.allChildren.firstIsInstanceOrNull<KtBackingField>() ?: return false
        return backingField.fieldKeyword != null
    }

    override fun getApplicableRanges(element: KtProperty): List<TextRange> =
        ApplicabilityRange.single(element) { it.fieldDeclaration?.fieldKeyword }

    override fun KaSession.prepareContext(element: KtProperty): Context {
        val initializerText = element.allChildren
            .firstIsInstanceOrNull<KtBackingField>()
            ?.let { computeInitializerText(it) }

        val containingClass = element.containingClassOrObject
        val references = containingClass
            ?.collectDescendantsOfType<KtNameReferenceExpression>()
            ?.filter { isReferenceTo(it, element) }
            ?.filter { isRenameCandidate(it, containingClass) }
            ?.map { it.createSmartPointer() }
            ?: emptyList()
        return Context(references, initializerText)
    }

    override fun createQuickFix(
        element: KtProperty,
        context: Context,
    ): KotlinModCommandQuickFix<KtProperty> = object : KotlinModCommandQuickFix<KtProperty>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.explicit.backing.field.with.private.property")

        override fun applyFix(project: Project, element: KtProperty, updater: ModPsiUpdater) {
            val backingField = element.allChildren.firstIsInstanceOrNull<KtBackingField>() ?: return
            renameOccurrences(element, context.references, updater)
            val backingProperty = createBackingProperty(element, backingField, context.backingFieldInitializerText)
            element.parent.addBefore(backingProperty, element)
            val commentSaver = CommentSaver(backingField)
            val getter = backingField.replace(createGetter(element))
            commentSaver.restore(getter)
            element.reformat(canChangeWhiteSpacesOnly = true)
        }
    }

    private fun renameOccurrences(
        property: KtProperty,
        context: List<SmartPsiElementPointer<KtNameReferenceExpression>>,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(property.project)
        val backingPropertyName = backingName(property)
        context
            .mapNotNull { pointer -> pointer.element?.let { updater.getWritable(it) } }
            .forEach { ref ->
                val parentDotQualified = ref.parent as? KtDotQualifiedExpression
                val replacementText = if (ref.hasLocalDeclaration(backingPropertyName)) "this.$backingPropertyName" else backingPropertyName
                val refToReplace = if (parentDotQualified?.receiverExpression is KtThisExpression) parentDotQualified else ref
                refToReplace.replace(psiFactory.createExpression(replacementText))
            }
    }

    private fun KtNameReferenceExpression.hasLocalDeclaration(name: String): Boolean =
        generateSequence(this.parent) { it.parent }
            .takeWhile { it !is KtClassBody }
            .any { element ->
                when (element) {
                    is KtBlockExpression -> element.statements.any { it is KtProperty && it.name == name }
                    is KtFunctionLiteral -> element.valueParameters.any { it.name == name }
                    else -> false
                }
            }

    private fun KaSession.isReferenceTo(ref: KtNameReferenceExpression, property: KtProperty): Boolean {
        val propertySymbol = property.symbol
        val resolvedSymbol = ref.mainReference.resolveToSymbol()
        if (ref.getReferencedName() != property.name) return false
        return resolvedSymbol == propertySymbol ||
                (resolvedSymbol is KaBackingFieldSymbol && resolvedSymbol.owningProperty == propertySymbol)
    }

    private fun createGetter(element: KtProperty): KtPropertyAccessor {
        val body = "get() = ${backingName(element)}"
        return KtPsiFactory(element.project).createProperty("val x $body").getter!!
    }

    private fun createBackingProperty(property: KtProperty, backingField: KtBackingField, initializerText: String?): KtProperty {
        return KtPsiFactory(property.project).createProperty(
            modifiers = "private",
            name = backingName(property),
            type = backingField.typeReference?.text,
            initializer = initializerText,
            isVar = false,
        )
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.computeInitializerText(backingField: KtBackingField): String? {
        val initializer = backingField.initializer ?: return null
        if (backingField.typeReference != null) return initializer.text
        val callExpr = initializer as? KtCallExpression ?: return initializer.text
        if (callExpr.typeArgumentList != null) return initializer.text
        val classType = callExpr.expressionType as? KaClassType ?: return initializer.text
        val typeArgs = classType.typeArguments
        if (typeArgs.isEmpty()) return initializer.text
        val renderedTypeArgs = typeArgs.map { arg ->
            if (arg is KaStarTypeProjection) return initializer.text
            arg.type?.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT) ?: return initializer.text
        }
        val callee = callExpr.calleeExpression?.text ?: return initializer.text
        val valueArgs = callExpr.valueArgumentList?.text ?: "()"
        return "$callee<${renderedTypeArgs.joinToString(", ")}>$valueArgs"
    }

    private fun isRenameCandidate(ref: KtNameReferenceExpression, containingClass: PsiElement?): Boolean {
        val immediateClass = generateSequence(ref.parent) { it.parent }
            .filterIsInstance<KtClassBody>().firstOrNull()?.parent
        if (immediateClass != containingClass) return false
        val parent = ref.parent as? KtDotQualifiedExpression
        return parent == null || parent.selectorExpression != ref || parent.receiverExpression is KtThisExpression
    }

    private fun backingName(property: KtProperty): String {
        return if (property.nameIdentifier?.text?.startsWith('`') == true) "`_${property.name}`" else "_${property.name}"
    }
}
