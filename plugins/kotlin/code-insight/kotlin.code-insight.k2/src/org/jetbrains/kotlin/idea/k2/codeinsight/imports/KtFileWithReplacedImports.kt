// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.projectStructure.isDangling
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Represents a copy of [KtFile] with potentially replaced imports,
 * with facilities to analyze references in this file.
 */
internal class KtFileWithReplacedImports private constructor(
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
     * Allows one to analyze code in [ktFile] with respect to replaced imports.
     */
    fun analyze(action: KaSession.() -> Unit) {
        if (isCopyOfDanglingFile) {
            // it's safe to just analyze dangling file by itself
            analyze(ktFile, action)
        } else {
            // we have to use analyzeCopy on a copy of a real file
            analyzeCopy(ktFile, KaDanglingFileResolutionMode.PREFER_SELF, action)
        }
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