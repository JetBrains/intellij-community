// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.processor.EvaluationRootProcessor
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.util.text
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

open class CodeFragmentFromPsiBuilder(private val project: Project, val language: Language) : CodeFragmentBuilder() {
  private val dumbService: DumbService = DumbService.getInstance(project)

  open fun getVisitors(): List<EvaluationVisitor> = EvaluationVisitor.EP_NAME.extensions.toList()

  override fun build(file: VirtualFile, rootProcessor: EvaluationRootProcessor, featureName: String): CodeFragment {
    val psi = dumbService.runReadActionInSmartMode<PsiFile?> {
      PsiManager.getInstance(project).findFile(file)
    } ?: throw PsiConverterException("Cannot get PSI of file ${file.path}")

    val filePath = FilesHelper.getRelativeToProjectPath(project, file.path)
    val visitors = getVisitors().filter { it.language == language && it.feature == featureName }
    if (visitors.isEmpty()) throw IllegalStateException("No suitable visitors. language=$language, feature=$featureName")
    if (visitors.size > 1) throw IllegalStateException("More than 1 suitable visitors. language=$language, feature=$featureName. visitors=${visitors.joinToString { it.javaClass.simpleName }}")
    val fileTokens = getFileTokens(visitors.first(), psi)
    fileTokens.path = filePath
    fileTokens.text = file.text()
    return findRoot(fileTokens, rootProcessor)
  }

  private fun getFileTokens(visitor: EvaluationVisitor, psi: PsiElement): CodeFragment {
    if (visitor !is PsiElementVisitor) throw IllegalArgumentException("Visitor ${visitor.javaClass.simpleName} must implement PsiElementVisitor")
    dumbService.runReadActionInSmartMode {
      assert(!dumbService.isDumb) { "Generating actions during indexing." }
      psi.accept(visitor)
    }
    return visitor.getFile()
  }
}
