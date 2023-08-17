// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Temporary service to unify implementations of "Unused import" inspections.
 */
interface KotlinOptimizeImportsFacility {
    interface ImportData {
        val unusedImports: List<KtImportDirective>
    }

    fun analyzeImports(file: KtFile): ImportData?

    /**
     * Returns `null` if the imports are already optimized and don't need to be edited.
     */
    fun prepareOptimizedImports(file: KtFile, data: ImportData): List<ImportPath>?

    // TODO Consider moving it to companion object
    fun replaceImports(file: KtFile, imports: Iterable<ImportPath>) {
        val manager = PsiDocumentManager.getInstance(file.project)
        manager.getDocument(file)?.let { manager.commitDocument(it) }
        val importList = file.importList ?: return
        val oldImports = importList.imports
        val psiFactory = KtPsiFactory(file.project)
        for (importPath in imports) {
            val newImport = importList.addBefore(
                psiFactory.createImportDirective(importPath),
                oldImports.lastOrNull()
            ) // insert into the middle to keep collapsed state

            importList.addAfter(psiFactory.createWhiteSpace("\n"), newImport)
        }

        // remove old imports after adding new ones to keep imports folding state
        for (import in oldImports) {
            import.delete()
        }
    }

    companion object {
        fun getInstance(): KotlinOptimizeImportsFacility = service()
    }
}