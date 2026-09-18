// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertLateinitPropertyToNotNullDelegateFix
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

internal object ConvertLateinitPropertyToNotNullDelegateFixFactory {

    val convertLateinitPropertyToNotNullDelegateFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InapplicableLateinitModifier ->
            val property = diagnostic.psi as? KtProperty ?: return@ModCommandBased emptyList()
            if (!property.hasModifier(KtTokens.LATEINIT_KEYWORD) || !property.isVar || property.hasInitializer()) {
                return@ModCommandBased emptyList()
            }
            val type = property.typeReference?.type ?: return@ModCommandBased emptyList()
            if (type.classId !in KaStandardTypeClassIds.PRIMITIVES || type.isMarkedNullable) return@ModCommandBased emptyList()

            val renderedType = KaRenderer.withShortNames.renderToString(type)

            listOf(
                ConvertLateinitPropertyToNotNullDelegateFix(property, renderedType)
            )
        }
}
