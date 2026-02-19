// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddDefaultConstructorFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

internal object AddDefaultConstructorFixFactory {
    val addDefaultConstructorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoImplicitDefaultConstructorOnExpectClass ->
        val baseClass = elementToBaseClass(diagnostic.psi) ?: return@ModCommandBased emptyList()

        listOf(
            AddDefaultConstructorFix(baseClass)
        )
    }
}

private fun KaSession.elementToBaseClass(element: PsiElement): KtClass? {
    return when (element) {
        is KtSuperTypeCallEntry -> superTypeEntryToClass(element)
        is KtAnnotationEntry -> annotationEntryToClass(element)
        else -> null
    }
}

private fun KaSession.superTypeEntryToClass(typeEntry: KtSuperTypeListEntry): KtClass? {
    if ((typeEntry as? KtSuperTypeCallEntry)?.valueArguments?.isNotEmpty() == true) {
        return null // Don't suggest the quick fix because the default constructor should not have arguments
    }
    val baseType = typeEntry.typeReference?.type ?: return null
    val baseClassSymbol = baseType.expandedSymbol ?: return null
    if (!baseClassSymbol.isExpect) return null
    if (baseClassSymbol.classKind != KaClassKind.CLASS) return null
    return baseClassSymbol.psi as? KtClass
}

private fun KaSession.annotationEntryToClass(entry: KtAnnotationEntry): KtClass? {
    if (entry.valueArguments.isNotEmpty()) {
        return null // Don't suggest the quick fix because the default constructor should not have arguments
    }
    val symbol = entry.typeReference?.type?.expandedSymbol ?: return null
    if (!symbol.isExpect) return null
    return symbol.psi as? KtClass
}
