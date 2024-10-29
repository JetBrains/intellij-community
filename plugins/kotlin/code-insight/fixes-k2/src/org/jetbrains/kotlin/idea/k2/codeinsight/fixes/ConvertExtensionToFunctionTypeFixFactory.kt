// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaAnnotationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.types.Variance

internal object ConvertExtensionToFunctionTypeFixFactory {
    /**
     * Renders the [functionType] but moves a potential receiver type to be a parameter instead.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.renderFunctionTypeWithoutReceiver(functionType: KaFunctionType, renderer: KaTypeRenderer): String {
        val renderedParametersList = buildList {
            addIfNotNull(functionType.receiverType)
            addAll(functionType.parameterTypes)
        }.joinToString(", ", "(", ")") {
            it.render(renderer, Variance.INVARIANT)
        }
        val renderedReturnType = functionType.returnType.render(renderer, Variance.INVARIANT)
        return "$renderedParametersList -> $renderedReturnType"
    }

    @OptIn(KaExperimentalApi::class)
    private val shortNameRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
        annotationsRenderer = KaAnnotationRendererForSource.WITH_SHORT_NAMES.with {
            annotationFilter = KaRendererAnnotationsFilter.NONE
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createQuickFix(typeReference: KtTypeReference): ConvertExtensionToFunctionTypeFix? {
        val type = typeReference.type as? KaFunctionType ?: return null
        if (!type.hasReceiver) return null
        // We do not support rendering context receivers
        if (type.contextReceivers.isNotEmpty()) return null

        val shortName = renderFunctionTypeWithoutReceiver(type, shortNameRenderer)
        val longName = renderFunctionTypeWithoutReceiver(type, KaTypeRendererForSource.WITH_QUALIFIED_NAMES)
        return ConvertExtensionToFunctionTypeFix(typeReference, shortName, longName)
    }

    val superTypeIsExtensionFunctionType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SupertypeIsExtensionFunctionType ->
            val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
            listOfNotNull(createQuickFix(typeReference))
        }
}