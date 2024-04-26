// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.quickfix.ImportFixBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class K1ReferenceImporterFacility : KotlinReferenceImporterFacility {
    override fun createImportFixesForExpression(expression: KtExpression): Sequence<ImportFixBase<*>> {
        val file = expression.containingKtFile
        if (file.hasUnresolvedImportWhichCanImport(expression)) return emptySequence()

        return Fe10QuickFixProvider.getInstance(file.project)
            .createUnresolvedReferenceQuickFixesForElement(expression)
            .values.asSequence().flatten()
            .filterIsInstance<ImportFixBase<*>>()
            // obtained quick fix might be intended for an element different from `useSiteElement`, so we need to check again
            .filter { importFix -> importFix.element?.let(file::hasUnresolvedImportWhichCanImport) == false }
    }
}

private fun KtFile.hasUnresolvedImportWhichCanImport(element: PsiElement): Boolean {
    if (element !is KtSimpleNameExpression) return false
    val referencedName = element.getReferencedName()

    return importDirectives.any {
        (it.isAllUnder || it.importPath?.importedName?.asString() == referencedName) && it.targetDescriptors().isEmpty()
    }
}
