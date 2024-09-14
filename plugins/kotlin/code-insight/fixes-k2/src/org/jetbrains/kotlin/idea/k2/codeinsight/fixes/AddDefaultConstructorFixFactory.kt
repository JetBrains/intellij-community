// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddDefaultConstructorFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddDefaultConstructorFixFactory {

    val addDefaultConstructorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        val baseClass = elementToBaseClass(diagnostic.psi) ?: return@ModCommandBased emptyList()

        listOf(
            AddDefaultConstructorFix(baseClass)
        )
    }
}

private fun KaSession.elementToBaseClass(element: PsiElement): KtClass? {
    return when {
        element is KtConstructorCalleeExpression ->
            element.getStrictParentOfType<KtClassOrObject>()
                ?.superTypeListEntries
                ?.asSequence()
                ?.filterIsInstance<KtSuperTypeCallEntry>()
                ?.firstOrNull()?.let {
                    superTypeEntryToClass(it)
                }


        element is KtNameReferenceExpression && element.parent is KtUserType ->
            element.getStrictParentOfType<KtAnnotationEntry>()
                ?.let {
                    annotationEntryToClass(it)
                }

        else -> null
    }
}

private fun KaSession.superTypeEntryToClass(typeEntry: KtSuperTypeListEntry): KtClass? {
    val baseType = typeEntry.typeReference?.type ?: return null
    val baseClassSymbol = baseType.expandedSymbol ?: return null
    if (!baseClassSymbol.isExpect) return null
    if (baseClassSymbol.classKind != KaClassKind.CLASS) return null
    return baseClassSymbol.psi as? KtClass
}

private fun KaSession.annotationEntryToClass(entry: KtAnnotationEntry): KtClass? {
    val symbol = entry.typeReference?.type?.expandedSymbol ?: return null
    if (!symbol.isExpect) return null
    return symbol.psi as? KtClass
}
