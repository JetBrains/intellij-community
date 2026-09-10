// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.recentFiles

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters

internal class SeRecentFilesGotoItemProvider(
  private val project: Project,
  context: PsiElement?,
  model: GotoFileModel,
) : GotoFileItemProvider(project, context, model) {

  override fun filterElementsWithWeights(
    base: ChooseByNameViewModel,
    parameters: FindSymbolParameters,
    indicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<*>>,
  ): Boolean {
    val pattern = base.transformPattern(parameters.completePattern)
    val matcher = createMatcher(pattern)
    val psiManager = PsiManager.getInstance(project)

    // An open file already shows in the editor, so the recent list hides it.
    val opened = FileEditorManager.getInstance(project).selectedFiles.toHashSet()
    val seen = HashSet<VirtualFile>()

    // The history holds the oldest file first, so the reversed view puts the newest one on top.
    val history = EditorHistoryManager.getInstance(project).fileList.asReversed()

    for ((index, file) in history.withIndex()) {
      indicator.checkCanceled()

      if (!seen.add(file) || file in opened || !file.isValid) continue
      if (pattern.isNotEmpty() && !matcher.matches(file.name)) continue

      val psiFile = psiManager.findFile(file) ?: continue

      // An empty pattern matches every name with the same degree, so only the recency can order the list.
      val weight = if (pattern.isEmpty()) history.lastIndex - index else matcher.matchingDegree(file.name)

      if (!consumer.process(FoundItemDescriptor(psiFile, weight))) return false
    }

    return true
  }

  /**
   * Builds the matcher of the file names. It repeats `RecentFilesSEContributor.createMatcher`.
   */
  private fun createMatcher(pattern: String): MinusculeMatcher {
    val builder = NameUtil.buildMatcher("*$pattern")
    // A pattern that already starts with a wildcard asks for a match in any place.
    return (if (pattern.startsWith("*")) builder else builder.preferringStartMatches()).build()
  }
}
