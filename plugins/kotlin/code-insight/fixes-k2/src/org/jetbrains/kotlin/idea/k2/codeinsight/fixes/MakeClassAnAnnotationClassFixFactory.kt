// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.MakeClassAnAnnotationClassFix
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object MakeClassAnAnnotationClassFixFactory {

    val makeClassAnAnnotationClassFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NotAnAnnotationClass ->
        val typeReference = diagnostic.psi.getNonStrictParentOfType<KtAnnotationEntry>()?.typeReference ?: return@ModCommandBased emptyList()
        val classSymbol = typeReference.type.expandedSymbol ?: return@ModCommandBased emptyList()
        val klass = classSymbol.psi as? KtClass ?: return@ModCommandBased emptyList()
        if (!klass.canRefactorElement()) return@ModCommandBased emptyList()

        listOf(
            MakeClassAnAnnotationClassFix(klass)
        )
    }
}
