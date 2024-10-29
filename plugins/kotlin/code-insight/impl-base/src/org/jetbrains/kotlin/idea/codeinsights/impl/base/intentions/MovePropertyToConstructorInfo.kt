// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

sealed interface MovePropertyToConstructorInfo {
    data class ReplacementParameter(
        val constructorParameterToReplace: SmartPsiElementPointer<KtParameter>,
        val propertyAnnotationsText: String?,
    ) : MovePropertyToConstructorInfo {
        override fun toWritable(updater: ModPsiUpdater): MovePropertyToConstructorInfo =
            ReplacementParameter(
                constructorParameterToReplace.dereference()?.let { updater.getWritable(it).createSmartPointer() }
                    ?: constructorParameterToReplace,
                propertyAnnotationsText
            )

    }

    data class AdditionalParameter(
        val parameterTypeText: String,
        val propertyAnnotationsText: String?,
    ) : MovePropertyToConstructorInfo {
        override fun toWritable(updater: ModPsiUpdater): MovePropertyToConstructorInfo = this
    }

    fun toWritable(updater: ModPsiUpdater): MovePropertyToConstructorInfo

    companion object {
        context(KaSession)
        @OptIn(KaExperimentalApi::class)
        fun create(element: KtProperty, initializer: KtExpression? = element.initializer): MovePropertyToConstructorInfo? {
            if (initializer != null && !initializer.isValidInConstructor()) return null

            val propertyAnnotationsText = element.collectAnnotationsAsText()
            val constructorParameter = initializer?.findConstructorParameter()

            if (constructorParameter != null) {
                return ReplacementParameter(
                    constructorParameterToReplace = constructorParameter.createSmartPointer(),
                    propertyAnnotationsText = propertyAnnotationsText,
                )
            } else {
                val typeText = element.typeReference?.text ?: element.symbol.returnType.render(position = Variance.INVARIANT)
                return AdditionalParameter(
                    parameterTypeText = typeText,
                    propertyAnnotationsText = propertyAnnotationsText,
                )
            }
        }

        context(KaSession)
        private fun KtExpression.isValidInConstructor(): Boolean {
            val parentClassSymbol = getStrictParentOfType<KtClass>()?.classSymbol ?: return false
            var isValid = true
            accept(referenceExpressionRecursiveVisitor { expression ->
                if (!isValid) return@referenceExpressionRecursiveVisitor
                for (reference in expression.references.filterIsInstance<KtReference>()) {
                    for (classSymbol in reference.resolveToSymbols().filterIsInstance<KaClassSymbol>()) {
                        if (classSymbol == parentClassSymbol) {
                            isValid = false
                            return@referenceExpressionRecursiveVisitor
                        }
                    }
                }
            })

            return isValid
        }

        context(KaSession)
        private fun KtProperty.collectAnnotationsAsText(): String? = modifierList?.annotationEntries?.joinToString(separator = " ") {
            it.getTextWithUseSite()
        }

        context(KaSession)
        @OptIn(KaExperimentalApi::class)
        private fun KtAnnotationEntry.getTextWithUseSite(): String {
            if (useSiteTarget != null) return text
            val typeReference = typeReference ?: return text

            val applicableTargets = typeReference.type.expandedSymbol?.annotationApplicableTargets ?: return text

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

        context(KaSession)
        private fun KtExpression.findConstructorParameter(): KtParameter? {
            val constructorParam = mainReference?.resolveToSymbol() as? KaValueParameterSymbol ?: return null
            return constructorParam.psi as? KtParameter
        }
    }
}