// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile

class KotlinFirImportOptimizer : ImportOptimizer {
    private val optimizeImportsFacility: KotlinOptimizeImportsFacility
        get() = KotlinOptimizeImportsFacility.getInstance()

    override fun supports(file: PsiFile): Boolean = file is KtFile

    override fun processFile(file: PsiFile): ImportOptimizer.CollectingInfoRunnable {
        require(file is KtFile)

        val analysisResult = optimizeImportsFacility.analyzeImports(file) ?: return DO_NOTHING
        val optimizedImports = optimizeImportsFacility.prepareOptimizedImports(file, analysisResult) ?: return DO_NOTHING

        val removedImportsCount = analysisResult.unusedImports.size
        val addedImportsCount = 0

        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                optimizeImportsFacility.replaceImports(file, optimizedImports)
            }

            override fun getUserNotificationInfo(): String = if (removedImportsCount == 0) {
                KotlinBundle.message("import.optimizer.text.zero")
            } else {
                KotlinBundle.message(
                    "import.optimizer.text.non.zero",
                    removedImportsCount,
                    KotlinBundle.message("import.optimizer.text.import", removedImportsCount),
                    addedImportsCount,
                    KotlinBundle.message("import.optimizer.text.import", addedImportsCount),
                )
            }
        }
    }
}

private val DO_NOTHING: ImportOptimizer.CollectingInfoRunnable = object : ImportOptimizer.CollectingInfoRunnable {
    override fun run() {}
    override fun getUserNotificationInfo(): @HintText String =
        KotlinBundle.message("import.optimizer.notification.text.unused.imports.not.found")
}