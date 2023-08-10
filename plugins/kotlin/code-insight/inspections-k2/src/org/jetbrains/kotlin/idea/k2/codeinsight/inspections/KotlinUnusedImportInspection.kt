// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinUnusedImportInspection : AbstractKotlinInspection() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor>? {
        if (file !is KtFile) return null

        val result = analyze(file) {
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
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        return problems.toTypedArray()
    }
}

private class KotlinOptimizeImportsQuickFix(file: KtFile) : LocalQuickFixOnPsiElement(file) {
    override fun getText() = KotlinBundle.message("optimize.imports")
    override fun getFamilyName() = name
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        OptimizeImportsProcessor(project, file).run()
    }
    override fun startInWriteAction(): Boolean = false
}