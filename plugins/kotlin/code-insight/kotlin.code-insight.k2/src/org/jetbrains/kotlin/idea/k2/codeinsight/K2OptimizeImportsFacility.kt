// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

internal class K2OptimizeImportsFacility : KotlinOptimizeImportsFacility {
    private class K2ImportData(override val unusedImports: List<KtImportDirective>) : KotlinOptimizeImportsFacility.ImportData

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        val unusedImports = analyze(file) {
            analyseImports(file).unusedImports
        }

        return K2ImportData(unusedImports.toList())
    }

    override fun prepareOptimizedImports(
        file: KtFile,
        data: KotlinOptimizeImportsFacility.ImportData,
    ): List<ImportPath>? {
        require(data is K2ImportData)

        if (data.unusedImports.isEmpty()) return null
        return file.importDirectives.filterNot { it in data.unusedImports }.mapNotNull { it.importPath }
    }

    override fun replaceImports(
        file: KtFile,
        imports: Iterable<ImportPath>,
    ) {
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
}