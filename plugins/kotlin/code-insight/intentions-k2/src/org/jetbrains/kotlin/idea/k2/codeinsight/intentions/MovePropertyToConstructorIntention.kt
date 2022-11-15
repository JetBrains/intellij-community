// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.KtArrayAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtEnumEntryAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.calls.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildAdditionalConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.buildReplacementConstructorParameterText
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class MovePropertyToConstructorIntention :
    AbstractKotlinApplicableIntentionWithContext<KtProperty, MovePropertyToConstructorIntention.Context>(KtProperty::class) {

    sealed class Context

    data class ReplacementParameterContext(
        val constructorParameterToReplace: SmartPsiElementPointer<KtParameter>,
        val propertyAnnotationsText: String?,
    ) : Context()

    data class AdditionalParameterContext(
        val parameterTypeText: String,
        val propertyAnnotationsText: String?,
    ) : Context()

    override fun getFamilyName() = KotlinBundle.message("move.to.constructor")
    override fun getActionName(element: KtProperty, context: Context) = familyName
    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> = applicabilityTarget { it }

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
            val typeText = element.typeReference?.text ?: element.getVariableSymbol().returnType.render()
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
        this.accept(object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                element.acceptChildren(this)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                for (reference in expression.references.filterIsInstance<KtReference>()) {
                    for (classSymbol in reference.resolveToSymbols().filterIsInstance<KtClassOrObjectSymbol>()) {
                        if (classSymbol == parentClassSymbol) {
                            isValid = false
                        }
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
        val typeReferenceText = typeReference.text
        val valueArgumentList = valueArgumentList?.text.orEmpty()

        val applicableTargets = (typeReference.getKtType() as? KtNonErrorClassType)
            ?.classSymbol
            ?.getAnnotationApplicableTargetSet()
            ?: return text

        fun AnnotationUseSiteTarget.textWithMe() = "@$renderName:$typeReferenceText$valueArgumentList"

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
    private fun KtClassLikeSymbol.getAnnotationApplicableTargetSet(): Set<KotlinTarget> {
        val targetsArrayValue = annotationsByClassId(StandardNames.FqNames.targetClassId)
            .singleOrNull()
            ?.arguments
            ?.firstOrNull { it.name == ALLOWED_TARGETS_IDENTIFIER }
            ?.expression
                as? KtArrayAnnotationValue
        return targetsArrayValue
            ?.values
            ?.filterIsInstance<KtEnumEntryAnnotationValue>()
            ?.mapNotNull { annotationValue -> annotationValue.callableId?.callableName?.identifier?.let { KotlinTarget.valueOrNull(it) } }
            ?.toSet()
            ?: KotlinTarget.DEFAULT_TARGET_SET
    }

    context(KtAnalysisSession)
    private fun KtProperty.findConstructorParameter(): KtParameter? {
        val constructorParam = initializer?.resolveCall()?.successfulVariableAccessCall()?.symbol as? KtValueParameterSymbol ?: return null
        return constructorParam.psi as? KtParameter
    }

    override fun apply(element: KtProperty, context: Context, project: Project, editor: Editor?) {
        val factory = KtPsiFactory(element)
        val commentSaver = CommentSaver(element)

        when (context) {
            is ReplacementParameterContext -> {
                val constructorParameter = context.constructorParameterToReplace.dereference() ?: return
                val parameterText = element.buildReplacementConstructorParameterText(constructorParameter, context.propertyAnnotationsText)
                constructorParameter.replace(factory.createParameter(parameterText)).apply {
                    commentSaver.restore(this)
                }
            }

            is AdditionalParameterContext -> {
                val parameterText =
                    element.buildAdditionalConstructorParameterText(context.parameterTypeText, context.propertyAnnotationsText)
                val containingClass = element.getStrictParentOfType<KtClass>() ?: return
                containingClass.createPrimaryConstructorParameterListIfAbsent().addParameter(factory.createParameter(parameterText)).apply {
                    ShortenReferencesFacility.getInstance().shorten(this)
                    commentSaver.restore(this)
                }
            }
        }

        element.delete()
    }

    companion object {
        private val ALLOWED_TARGETS_IDENTIFIER = Name.identifier("allowedTargets")
    }
}
