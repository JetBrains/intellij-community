// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationTarget
import org.jetbrains.kotlin.analysis.api.components.applicableAnnotationTargets
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.referenceExpressionRecursiveVisitor
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
        // This is needed to always render the lower bounds for flexible types.
        // Flexible types cannot be represented in Kotlin, so without this renderer,
        // syntax errors might be caused by flexible types.
        @OptIn(KaExperimentalApi::class)
        private val LOWER_FLEXIBLE_BOUND_TYPE_RENDERER: KaTypeRenderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
            flexibleTypeRenderer = object : KaFlexibleTypeRenderer {
                override fun renderType(
                    analysisSession: KaSession,
                    type: KaFlexibleType,
                    typeRenderer: KaTypeRenderer,
                    printer: PrettyPrinter
                ) {
                    typeRenderer.renderType(analysisSession, type.lowerBound, printer)
                }
            }
        }

        @OptIn(KaExperimentalApi::class)
        context(_: KaSession)
        fun create(element: KtProperty, initializer: KtExpression? = element.initializer): MovePropertyToConstructorInfo? {
            if (initializer != null && !initializer.isValidInConstructor()) return null

            val propertyAnnotationsText = element.collectAnnotationsAsText()
            val constructorParameter = initializer?.findConstructorParameter()

            if (constructorParameter != null) {
                if (!constructorParameter.isValidInConstructor()) return null
                return ReplacementParameter(
                    constructorParameterToReplace = constructorParameter.createSmartPointer(),
                    propertyAnnotationsText = propertyAnnotationsText,
                )
            } else {
                val typeText = element.typeReference?.text ?: element.symbol.returnType.render(
                    position = Variance.INVARIANT,
                    renderer = LOWER_FLEXIBLE_BOUND_TYPE_RENDERER
                )
                return AdditionalParameter(
                    parameterTypeText = typeText,
                    propertyAnnotationsText = propertyAnnotationsText,
                )
            }
        }

        context(_: KaSession)
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

        context(_: KaSession)
        private fun KtProperty.collectAnnotationsAsText(): String? = modifierList?.annotationEntries?.joinToString(separator = " ") {
            it.getTextWithUseSite()
        }

        @OptIn(KaExperimentalApi::class)
        context(_: KaSession)
        private fun KtAnnotationEntry.getTextWithUseSite(): String {
            if (useSiteTarget != null) return text
            val typeReference = typeReference ?: return text

            val applicableTargets = typeReference.type.expandedSymbol?.applicableAnnotationTargets ?: return text

            fun AnnotationUseSiteTarget.textWithMe() = "@$renderName:${typeReference.text}${valueArgumentList?.text.orEmpty()}"

            return when {
                KaAnnotationTarget.VALUE_PARAMETER !in applicableTargets ->
                    text

                KaAnnotationTarget.PROPERTY in applicableTargets ->
                    AnnotationUseSiteTarget.PROPERTY.textWithMe()

                KaAnnotationTarget.FIELD in applicableTargets ->
                    AnnotationUseSiteTarget.FIELD.textWithMe()

                else ->
                    text
            }
        }

        context(_: KaSession)
        private fun KtExpression.findConstructorParameter(): KtParameter? {
            val constructorParam = mainReference?.resolveToSymbol() as? KaValueParameterSymbol ?: return null
            return constructorParam.psi as? KtParameter
        }
    }
}