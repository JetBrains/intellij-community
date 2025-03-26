// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.projectStructure.isDangling
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Represents a copy of [KtFile] with potentially replaced imports,
 * with facilities to analyze references in this file.
 */
@ApiStatus.Internal
class KtFileWithReplacedImports private constructor(
    val ktFile: KtFile,
    private val isCopyOfDanglingFile: Boolean,
) {
    /**
     * Replaces a list of imports in the [ktFile].
     */
    fun setImports(imports: List<ImportPath>) {
        KotlinOptimizeImportsFacility.getInstance().replaceImports(ktFile, imports)
    }

    /**
     * Adds [importFqName] to the [ktFile] for the duration of the [action], and removes it afterward.
     */
    fun <T> withExtraImport(importFqName: FqName, action: () -> T): T {
        val oldImportsCopy = ktFile.importDirectives.toList()
        val newImportDirective = ktFile.addImport(fqName = importFqName)

        try {
            return action()
        } finally {
            if (newImportDirective !in oldImportsCopy) {
                newImportDirective.delete()
            }
        }
    }

    /**
     * Finds an element in the [ktFile] which is in the same position as the [originalElement] in the original file.
     * 
     * Returns `null` if it could not find the matching copy element.
     */
    fun <T : PsiElement> findMatchingElement(originalElement: T): T? =
        try {
            PsiTreeUtil.findSameElementInCopy(originalElement, ktFile)
        } catch (_: IllegalStateException) {
            null
        }

    /**
     * Allows one to analyze code in [ktFile] with respect to replaced imports.
     */
    fun <T> analyze(action: KaSession.() -> T): T =
        if (isCopyOfDanglingFile) {
            // it's safe to just analyze dangling file by itself
            analyze(ktFile, action)
        } else {
            // we have to use analyzeCopy on a copy of a real file
            analyzeCopy(ktFile, KaDanglingFileResolutionMode.PREFER_SELF, action)
        }

    companion object {
        fun createFrom(originalFile: KtFile): KtFileWithReplacedImports {
            val isCopyOfDangling = originalFile.isDangling

            val copyFile = if (isCopyOfDangling) {
                createCopyOfDanglingFile(originalFile)
            } else {
                originalFile.copied()
            }

            return KtFileWithReplacedImports(copyFile, isCopyOfDangling)
        }
    }
}

private fun createCopyOfDanglingFile(danglingKtFile: KtFile): KtFile {
    require(danglingKtFile.isDangling) { "Should be executed only on dangling files" }

    val psiFactory = KtPsiFactory(danglingKtFile.project)
    return psiFactory.createFile("fakeFileWithSubstitutedImports.kt", danglingKtFile.text).apply {
        // to make analysis of this file equivalent to the original dangling file
        analysisContext = danglingKtFile.analysisContext
    }
}