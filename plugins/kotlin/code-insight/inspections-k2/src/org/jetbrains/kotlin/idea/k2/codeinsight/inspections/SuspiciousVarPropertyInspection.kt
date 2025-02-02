// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

internal class SuspiciousVarPropertyInspection : KotlinApplicableInspectionBase<KtProperty, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtProperty): List<TextRange> {
        return ApplicabilityRange.single(element) { it.valOrVarKeyword }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtProperty,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("suspicious.var.property.its.setter.does.not.influence.its.getter.result"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        IntentionWrapper(ChangeVariableMutabilityFix(element, makeVar = false, deleteInitializer = true))
    )

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.isLocal || !element.isVar || element.initializer == null || element.setter != null) return false
        return element.getter != null && !element.hasDelegate()
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val getter = element.getter ?: return null
        if (doesOverrideVar(element)) return null
        if (!isBackingFieldRequired(element)) return null
        if (hasBackingFieldReference(getter)) return null
        return Unit
    }

    private fun KaSession.doesOverrideVar(element: KtProperty): Boolean =
        element.hasModifier(KtTokens.OVERRIDE_KEYWORD) && element.symbol
            .allOverriddenSymbols
            .filterIsInstance<KaPropertySymbol>()
            .any { !it.isVal }


    private fun KaSession.isBackingFieldRequired(property: KtProperty): Boolean {
        val getter = property.getter
        val resolvedGetter = getter?.symbol
        val setter = property.setter
        val resolvedSetter = setter?.symbol

        if (getter == null) return true
        if (property.isVar && setter == null) return true
        if (resolvedSetter != null && !setter.hasBody() && resolvedSetter.modality != KaSymbolModality.ABSTRACT) return true
        if (!getter.hasBody() && resolvedGetter?.modality != KaSymbolModality.ABSTRACT) return true

        return false
    }

    private fun KaSession.hasBackingFieldReference(accessor: KtPropertyAccessor): Boolean {
        val bodyExpression = accessor.bodyExpression ?: return true
        if (isBackingFieldReference(bodyExpression, accessor.property)) return true
        return bodyExpression.anyDescendantOfType<KtNameReferenceExpression> {
            isBackingFieldReference(it, accessor.property)
        }
    }

    private fun KaSession.isBackingFieldReference(expression: KtExpression, property: KtProperty): Boolean =
        expression is KtNameReferenceExpression && isBackingFieldReference(expression, property)

    private fun KaSession.isBackingFieldReference(namedReference: KtNameReferenceExpression, property: KtProperty): Boolean {
        if (namedReference.text != KtTokens.FIELD_KEYWORD.value) return false
        val fieldSymbol = namedReference.mainReference.resolveToSymbol()
        if (fieldSymbol !is KaBackingFieldSymbol) return false
        return fieldSymbol.owningProperty.psi == property
    }
}