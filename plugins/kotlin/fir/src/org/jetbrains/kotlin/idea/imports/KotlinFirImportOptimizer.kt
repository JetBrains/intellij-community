// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.components.KtImportOptimizerResult
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinFirImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is KtFile

    override fun processFile(file: PsiFile): ImportOptimizer.CollectingInfoRunnable {
        require(file is KtFile)

        val result = analyse(file) {
            analyseImports(file)
        }

        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                replaceImports(result)
            }

            override fun getUserNotificationInfo(): String? = null
        }
    }

    companion object {
        fun replaceImports(optimizationResult: KtImportOptimizerResult) {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            for (import in optimizationResult.unusedImports) {
                import.delete()
            }
        }
    }
}