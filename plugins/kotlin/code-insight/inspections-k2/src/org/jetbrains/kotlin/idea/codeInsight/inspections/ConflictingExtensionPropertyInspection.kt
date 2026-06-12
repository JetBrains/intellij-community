// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDeprecationLevel
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.syntheticJavaPropertiesScope
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.hasOrOverridesCallableId
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.propertyVisitor

@OptIn(KaExperimentalApi::class)
class ConflictingExtensionPropertyInspection : KotlinApplicableInspectionBase<KtProperty, Boolean>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = propertyVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.receiverTypeReference != null && element.nameIdentifier != null

    override fun getApplicableRanges(element: KtProperty): List<TextRange> =
        ApplicabilityRanges.declarationName(element)

    override fun KaSession.prepareContext(element: KtProperty): Boolean? {
        if (element.symbol.deprecation?.level == KaDeprecationLevel.HIDDEN) return null
        val conflictingExtension = element.conflictingSyntheticExtension() ?: return null
        return element.isSameAsSynthetic(conflictingExtension)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtProperty,
        context: Boolean,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fixes = if (context) {
            val deleteFix = SafeDeleteFix(element)
            if (onTheFly) arrayOf(deleteFix, MarkHiddenAndDeprecatedAction(element).asQuickFix()) else arrayOf(deleteFix)
        } else {
            emptyArray<LocalQuickFix>()
        }
        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBundle.message(
                "this.property.conflicts.with.synthetic.extension.and.should.be.removed.or.renamed.to.avoid.breaking.code.by.future.changes.in.the.compiler"
            ),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            *fixes,
        )
    }

    context(_: KaSession)
    private fun KtProperty.conflictingSyntheticExtension(): KaSyntheticJavaPropertySymbol? {
        val propertyName = nameAsName ?: return null
        val receiverType = receiverTypeReference?.type ?: return null
        return receiverType.syntheticJavaPropertiesScope
            ?.getCallableSignatures { it == propertyName }
            ?.firstNotNullOfOrNull { it.symbol as? KaSyntheticJavaPropertySymbol }
    }

    context(_: KaSession)
    private fun KtProperty.isSameAsSynthetic(syntheticProperty: KaSyntheticJavaPropertySymbol): Boolean {
        val getter = this.getter ?: return false
        val setter = this.setter

        if (!getter.checkGetterBodyIsGetMethodCall(syntheticProperty.javaGetterSymbol)) return false

        if (setter != null) {
            val setMethod = syntheticProperty.javaSetterSymbol ?: return false
            if (!setter.checkSetterBodyIsSetMethodCall(setMethod)) return false
        }

        return true
    }

    context(_: KaSession)
    private fun KtPropertyAccessor.checkGetterBodyIsGetMethodCall(syntheticGetter: KaCallableSymbol): Boolean {
        return if (hasBlockBody()) {
            (bodyBlockExpression?.statements?.singleOrNull() as? KtReturnExpression)?.returnedExpression.isMethodCall(syntheticGetter)
        } else {
            bodyExpression.isMethodCall(syntheticGetter)
        }
    }

    context(_: KaSession)
    private fun KtPropertyAccessor.checkSetterBodyIsSetMethodCall(syntheticSetter: KaCallableSymbol): Boolean {
        val valueParameterName = this.valueParameters.singleOrNull()?.nameAsName ?: return false
        return if (hasBlockBody()) {
            bodyBlockExpression?.statements?.singleOrNull().isSetterMethodCall(syntheticSetter, valueParameterName)
        } else {
            bodyExpression.isSetterMethodCall(syntheticSetter, valueParameterName)
        }
    }

    context(_: KaSession)
    private fun KtExpression?.isMethodCall(method: KaCallableSymbol): Boolean = when (this) {
        is KtCallExpression -> resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.matches(method) == true
        is KtQualifiedExpression -> {
            val receiver = receiverExpression
            receiver is KtThisExpression && receiver.labelQualifier == null && selectorExpression.isMethodCall(method)
        }

        else -> false
    }

    context(_: KaSession)
    private fun KtExpression?.isSetterMethodCall(method: KaCallableSymbol, valueParameterName: Name): Boolean = when (this) {
        is KtCallExpression -> {
            val argumentExpression = valueArguments.singleOrNull()?.getArgumentExpression() as? KtSimpleNameExpression
            argumentExpression?.getReferencedNameAsName() == valueParameterName &&
                    resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.matches(method) == true
        }

        is KtQualifiedExpression -> {
            val receiver = receiverExpression
            receiver is KtThisExpression && receiver.labelQualifier == null && selectorExpression.isSetterMethodCall(
                method,
                valueParameterName
            )
        }

        else -> false
    }

    context(_: KaSession)
    private fun KaCallableSymbol.matches(other: KaCallableSymbol): Boolean {
        if (this == other) return true

        val otherCallableId = other.callableId
        if (otherCallableId != null && hasOrOverridesCallableId(otherCallableId)) return true

        val thisCallableId = this.callableId
        return thisCallableId != null && other.hasOrOverridesCallableId(thisCallableId)
    }

    private class MarkHiddenAndDeprecatedAction(element: KtProperty) : PsiUpdateModCommandAction<KtProperty>(element) {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("mark.as.deprecated.level.deprecationlevel.hidden")

        override fun getPresentation(context: ActionContext, element: KtProperty): Presentation =
            Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

        override fun invoke(context: ActionContext, element: KtProperty, updater: ModPsiUpdater) {
            val factory = KtPsiFactory(context.project)
            val name = element.nameAsName!!.render()
            element.addAnnotationWithLineBreak(
                factory.createAnnotationEntry(
                    "@Deprecated(\"Is replaced with automatic synthetic extension\", ReplaceWith(\"$name\"), level = DeprecationLevel.HIDDEN)"
                )
            )
        }

        private fun KtNamedDeclaration.addAnnotationWithLineBreak(annotationEntry: KtAnnotationEntry): KtAnnotationEntry {
            val newLine = KtPsiFactory(project).createNewLine()
            val ktModifierList = modifierList
            val result = addAnnotationEntry(annotationEntry)
            if (ktModifierList != null) {
                ktModifierList.addAfter(newLine, result)
            } else {
                addAfter(newLine, ktModifierList)
            }
            return result
        }
    }
}
