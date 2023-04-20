// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildAdditionalConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildReplacementConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

class MovePropertyToConstructorIntention :
    AbstractKotlinApplicableIntentionWithContext<KtProperty, MovePropertyToConstructorIntention.Context>(KtProperty::class),
    LocalQuickFix {

    sealed interface Context

    data class ReplacementParameterContext(
        val constructorParameterToReplace: SmartPsiElementPointer<KtParameter>,
        val propertyAnnotationsText: String?,
    ) : Context

    data class AdditionalParameterContext(
        val parameterTypeText: String,
        val propertyAnnotationsText: String?,
    ) : Context

    override fun getFamilyName() = KotlinBundle.message("move.to.constructor")
    override fun getActionName(element: KtProperty, context: Context) = familyName
    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtProperty) = element.isMovableToConstructorByPsi()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtProperty): Context? {
        val initializer = element.initializer
        if (initializer != null && !initializer.isValidInConstructor()) return null

        val propertyAnnotationsText = element.collectAnnotationsAsText()
        val constructorParameter = element.findConstructorParameter()

        if (constructorParameter != null) {
            return ReplacementParameterContext(
                constructorParameterToReplace = constructorParameter.createSmartPointer(),
                propertyAnnotationsText = propertyAnnotationsText,
            )
        } else {
            val typeText = element.typeReference?.text ?: element.getVariableSymbol().returnType.render(position = Variance.INVARIANT)
            return AdditionalParameterContext(
                parameterTypeText = typeText,
                propertyAnnotationsText = propertyAnnotationsText,
            )
        }
    }

    context(KtAnalysisSession)
    private fun KtExpression.isValidInConstructor(): Boolean {
        val parentClassSymbol = getStrictParentOfType<KtClass>()?.getClassOrObjectSymbol() ?: return false
        var isValid = true
        accept(referenceExpressionRecursiveVisitor { expression ->
            if (!isValid) return@referenceExpressionRecursiveVisitor
            for (reference in expression.references.filterIsInstance<KtReference>()) {
                for (classSymbol in reference.resolveToSymbols().filterIsInstance<KtClassOrObjectSymbol>()) {
                    if (classSymbol == parentClassSymbol) {
                        isValid = false
                        return@referenceExpressionRecursiveVisitor
                    }
                }
            }
        })

        return isValid
    }

    context(KtAnalysisSession)
    private fun KtProperty.collectAnnotationsAsText(): String? = modifierList?.annotationEntries?.joinToString(separator = " ") {
        it.getTextWithUseSite()
    }

    context(KtAnalysisSession)
    private fun KtAnnotationEntry.getTextWithUseSite(): String {
        if (useSiteTarget != null) return text
        val typeReference = typeReference ?: return text

        val applicableTargets = typeReference.getKtType().expandedClassSymbol?.annotationApplicableTargets ?: return text

        fun AnnotationUseSiteTarget.textWithMe() = "@$renderName:${typeReference.text}${valueArgumentList?.text.orEmpty()}"

        return when {
            KotlinTarget.VALUE_PARAMETER !in applicableTargets ->
                text

            KotlinTarget.PROPERTY in applicableTargets ->
                AnnotationUseSiteTarget.PROPERTY.textWithMe()

            KotlinTarget.FIELD in applicableTargets ->
                AnnotationUseSiteTarget.FIELD.textWithMe()

            else ->
                text
        }
    }

    context(KtAnalysisSession)
    private fun KtProperty.findConstructorParameter(): KtParameter? {
        val constructorParam = initializer?.mainReference?.resolveToSymbol() as? KtValueParameterSymbol ?: return null
        return constructorParam.psi as? KtParameter
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty ?: return
        applyTo(property, null)
    }

    override fun apply(element: KtProperty, context: Context, project: Project, editor: Editor?) {
        val factory = KtPsiFactory.contextual(element, markGenerated = true)
        val commentSaver = CommentSaver(element)

        val newParameter = when (context) {
            is ReplacementParameterContext -> {
                val constructorParameter = context.constructorParameterToReplace.dereference() ?: return
                val parameterText = element.buildReplacementConstructorParameterText(constructorParameter, context.propertyAnnotationsText)
                constructorParameter.replace(factory.createParameter(parameterText))
            }

            is AdditionalParameterContext -> {
                val containingClass = element.getStrictParentOfType<KtClass>() ?: return
                val parameterText =
                    element.buildAdditionalConstructorParameterText(context.parameterTypeText, context.propertyAnnotationsText)
                containingClass.createPrimaryConstructorParameterListIfAbsent().addParameter(factory.createParameter(parameterText)).apply {
                    ShortenReferencesFacility.getInstance().shorten(this)
                }
            }
        }

        commentSaver.restore(newParameter)
        element.delete()
    }
}
