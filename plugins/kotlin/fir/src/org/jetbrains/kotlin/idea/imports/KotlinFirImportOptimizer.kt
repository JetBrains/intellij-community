/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.imports

import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinFirImportOptimizer : ImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is KtFile

    override fun processFile(file: PsiFile): ImportOptimizer.CollectingInfoRunnable {
        require(file is KtFile)

        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {}

            override fun getUserNotificationInfo(): String? = null
        }
    }
}