// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinFirImportOptimizer : ImportOptimizer {
    private val optimizeImportsFacility: KotlinOptimizeImportsFacility
        get() = KotlinOptimizeImportsFacility.getInstance()

    override fun supports(file: PsiFile): Boolean = file is KtFile

    override fun processFile(file: PsiFile): Runnable {
        require(file is KtFile)

        val analysisResult = optimizeImportsFacility.analyzeImports(file) ?: return DO_NOTHING
        val optimizedImports = optimizeImportsFacility.prepareOptimizedImports(file, analysisResult) ?: return DO_NOTHING

        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                optimizeImportsFacility.replaceImports(file, optimizedImports)
            }

            override fun getUserNotificationInfo(): String? = null
        }
    }
}

private val DO_NOTHING: Runnable = Runnable {}