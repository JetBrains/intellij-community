// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
import org.jetbrains.kotlin.idea.util.withQualifiedNames
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.idea.util.withoutAnnotations
import org.jetbrains.kotlin.psi.KtTypeReference

internal object ConvertExtensionToFunctionTypeFixFactory {
    /**
     * Renders the [functionType] but moves a potential receiver type to be a parameter instead.
     */
    context(session: KaSession)
    private fun renderFunctionTypeWithoutReceiver(functionType: KaFunctionType, renderer: KaRenderer): String {
        val renderedParametersList = buildList {
            addIfNotNull(functionType.receiverType)
            addAll(functionType.parameterTypes)
        }.joinToString(", ", "(", ")") {
            renderer.renderToString(it)
        }
        val renderedReturnType = renderer.renderToString(functionType.returnType)
        return "$renderedParametersList -> $renderedReturnType"
    }

    private val shortNameRenderer: KaRenderer = KaRenderer.withShortNames.copy { withoutAnnotations() }

    context(session: KaSession)
    private fun createQuickFix(typeReference: KtTypeReference): ConvertExtensionToFunctionTypeFix? {
        val type = typeReference.type as? KaFunctionType ?: return null
        if (!type.hasReceiver) return null
        // We do not support rendering context receivers
        if (type.contextParameterTypes.isNotEmpty()) return null

        val shortName = renderFunctionTypeWithoutReceiver(type, shortNameRenderer)
        val longName = renderFunctionTypeWithoutReceiver(type, KaRenderer.withQualifiedNames)
        return ConvertExtensionToFunctionTypeFix(typeReference, shortName, longName)
    }

    val superTypeIsExtensionFunctionType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SupertypeIsExtensionOrContextFunctionType ->
            val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
            listOfNotNull(createQuickFix(typeReference))
        }
}