// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile

/**
 * Simple quickfix for running import optimizer on Kotlin file.
 */
class KotlinOptimizeImportsQuickFix(file: KtFile) : LocalQuickFixOnPsiElement(file) {
    override fun getText(): String = KotlinBundle.message("optimize.imports")

    override fun getFamilyName(): String = name

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        OptimizeImportsProcessor(project, file).run()
    }

    override fun startInWriteAction(): Boolean = false
}