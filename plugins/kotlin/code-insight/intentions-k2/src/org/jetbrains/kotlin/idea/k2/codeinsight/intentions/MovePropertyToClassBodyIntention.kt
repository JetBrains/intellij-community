// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.KaAnnotationCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isApplicableTargetSet
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds.Annotations.ParameterNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter

class MovePropertyToClassBodyIntention : KotlinApplicableModCommandAction<KtParameter, Unit>(
    KtParameter::class
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("move.to.class.body")

    override fun invoke(
        actionContext: ActionContext,
        element: KtParameter,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val parentClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java) ?: return

        val propertyDeclaration = KtPsiFactory(element.project)
            .createProperty("${element.valOrVarKeyword?.text} ${element.name} = ${element.name}")

        val firstProperty = parentClass.getProperties().firstOrNull()
        parentClass.addDeclarationBefore(propertyDeclaration, firstProperty).apply {
            val propertyModifierList = element.modifierList?.copy() as? KtModifierList
            propertyModifierList?.getModifier(KtTokens.VARARG_KEYWORD)?.delete()
            propertyModifierList?.let { modifierList?.replace(it) ?: addBefore(it, firstChild) }
            // For the new property, we will have to add the annotations that (also) target the property
            // and remove any that do not.
            modifierList?.annotationEntries?.forEach {
                if (!it.isAppliedToProperty()) {
                    it.delete()
                } else if (it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY) {
                    it.useSiteTarget?.removeWithColon()
                }
            }
        }

        element.valOrVarKeyword?.delete()
        // For the annotations we retain, we need to remove the annotations (and use sites) that are no longer required
        val parameterAnnotationsText = element.modifierList?.annotationEntries
            ?.filter { it.isAppliedToConstructorParameter() }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " ") { it.textWithoutUseSite() }

        val hasVararg = element.hasModifier(KtTokens.VARARG_KEYWORD)
        if (parameterAnnotationsText != null) {
            element.modifierList?.replace(KtPsiFactory(element.project).createModifierList(parameterAnnotationsText))
        } else {
            element.modifierList?.delete()
        }

        if (hasVararg) element.addModifier(KtTokens.VARARG_KEYWORD)
    }

    private fun KtAnnotationEntry.isAppliedToProperty(): Boolean {
        useSiteTarget?.getAnnotationUseSiteTarget()?.let {
            return it == AnnotationUseSiteTarget.FIELD
                    || it == AnnotationUseSiteTarget.PROPERTY
                    || it == AnnotationUseSiteTarget.PROPERTY_GETTER
                    || it == AnnotationUseSiteTarget.PROPERTY_SETTER
                    || it == AnnotationUseSiteTarget.SETTER_PARAMETER
        }

        return !isApplicableToConstructorParameter()
    }

    private fun KtAnnotationEntry.isAppliedToConstructorParameter(): Boolean {
        useSiteTarget?.getAnnotationUseSiteTarget()?.let {
            return it == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
        }

        return isApplicableToConstructorParameter()
    }

    private val valueParameterTargetCallableId = CallableId(StandardClassIds.AnnotationTarget, Name.identifier("VALUE_PARAMETER"))
    private fun KaAnnotationValue.isValueParameterTargetValue(): Boolean {
        return when (this) {
            is KaAnnotationValue.ArrayValue -> values.any { it.isApplicableTargetSet(valueParameterTargetCallableId) }
            is KaAnnotationValue.EnumEntryValue -> callableId == StandardClassIds.Annotations.Target
            else -> false
        }
    }

    private fun KaAnnotation.isValueArgumentTarget(): Boolean {
        val allowedTargetArguments = arguments.filter { it.name == ParameterNames.targetAllowedTargets }
        return allowedTargetArguments.any { it.expression.isValueParameterTargetValue() }
    }

    private fun KtAnnotationEntry.isApplicableToConstructorParameter(): Boolean {
        analyze(this) {
            // Find all meta-annotations for this annotation to check if the annotation targets the constructor parameter
            val annotationClassSymbol = resolveToCall()?.successfulCallOrNull<KaAnnotationCall>()?.symbol?.containingSymbol as? KaClassSymbol ?: return false
            val targetAnnotations = annotationClassSymbol.annotations.filter { it.classId == StandardClassIds.Annotations.Target }
            // Without target annotation, it targets the constructor parameter by default
            if (targetAnnotations.isEmpty()) return true
            return targetAnnotations.any {
                it.isValueArgumentTarget()
            }
        }
    }

    private fun KtAnnotationEntry.textWithoutUseSite() = "@" + typeReference?.text.orEmpty() + valueArgumentList?.text.orEmpty()

    private fun KtAnnotationUseSiteTarget.removeWithColon() {
        nextSibling?.delete() // ':' symbol after use site
        delete()
    }

    override fun KaSession.prepareContext(element: KtParameter): Unit? {
        if (!element.isPropertyParameter()) return null
        val containingClass = element.containingClass() ?: return null
        return Unit.takeIf { !containingClass.mustHaveOnlyPropertiesInPrimaryConstructor() }
    }
}