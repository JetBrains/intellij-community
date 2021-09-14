// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.KotlinOptimizeImportsQuickFix
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinHLUnusedImportInspection : AbstractKotlinInspection() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor>? {
        if (file !is KtFile) return null

        val result = analyse(file) {
            analyseImports(file)
        }

        if (result.unusedImports.isEmpty()) return null

        val quickFixes = arrayOf(KotlinOptimizeImportsQuickFix(file))

        val problems = result.unusedImports.map { importPsiElement ->
            manager.createProblemDescriptor(
                importPsiElement,
                KotlinBundle.message("unused.import.directive"),
                isOnTheFly,
                quickFixes,
                ProblemHighlightType.LIKE_UNUSED_SYMBOL
            )
        }

        return problems.toTypedArray()
    }
}