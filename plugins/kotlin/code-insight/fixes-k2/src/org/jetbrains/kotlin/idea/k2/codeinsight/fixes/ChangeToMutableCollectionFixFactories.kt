// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.MutableCollectionsConversionUtils
import org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtProperty

object ChangeToMutableCollectionFixFactories {

    val noSetMethod: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoSetMethod> = KotlinQuickFixFactory.ModCommandBased {
        createQuickFixes(diagnostic = it)
    }

    private fun KaSession.createQuickFixes(diagnostic: KaFirDiagnostic.NoSetMethod): List<ModCommandAction> {
        val element = diagnostic.psi
        val arrayExpr = element.arrayExpression ?: return emptyList()
        val type = arrayExpr.expressionType as? KaClassType ?: return emptyList()

        val immutableCollectionClassId = type.classId
        if (!MutableCollectionsConversionUtils.isReadOnlyListOrMap(immutableCollectionClassId)) return emptyList()

        val property = arrayExpr.mainReference?.resolve() as? KtProperty ?: return emptyList()
        if (!MutableCollectionsConversionUtils.canConvertPropertyType(property)) return emptyList()

        return listOf(ChangeToMutableCollectionFix(property, immutableCollectionClassId))
    }
}
