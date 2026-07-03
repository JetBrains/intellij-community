package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import com.intellij.markdown.backend.services.MarkdownFileGraphUtils
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap

internal class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Contradiction> = ContradictionAnalyzer()
  override fun getDependencies(root: PsiFile): Set<PsiFile> = getAndCacheDependencies(root)
    .asSequence()
    .mapNotNull { root.manager.findFile(it) }
    .filter { isSpecificationLikeFile(it) }
    .toSet()

  private fun getAndCacheDependencies(root: PsiFile): Set<VirtualFile> {
    val project = root.project
    val cache = CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(
        ConcurrentHashMap<VirtualFile, Set<VirtualFile>>(),
        PsiModificationTracker.MODIFICATION_COUNT
      )
    }

    val file = root.virtualFile ?: throw IllegalStateException("Virtual file for a file was not found")
    var files = cache[file]
    if (files != null) return files

    files = MarkdownFileGraphUtils.getDependencySet(root) { isSpecificationLikeFile(it) }
    for (file in files) {
      cache.putIfAbsent(file, files)
    }
    return files
  }
}
