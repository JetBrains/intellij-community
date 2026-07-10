package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.markdown.backend.services.MarkdownFileGraphUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

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

  override fun getAdditionalFixes(issue: LlmIssue<Contradiction>, analyzedFiles: Set<PsiFile>): List<LocalQuickFix> {
    if (issue.issue.contradictsStartOffset() < 0) return emptyList()

    val file = analyzedFiles.asSequence()
      .mapNotNull { it.virtualFile }
      .firstOrNull { it.path == issue.issue.contradictsPath() } ?: return emptyList()
    return listOf(NavigateToContradictionQuickFix(file, issue.issue.contradictsStartOffset()))
  }

  private class NavigateToContradictionQuickFix(
    private val file: VirtualFile,
    private val offset: Int,
  ) : LocalQuickFix, Iconable {
    override fun getFamilyName(): String = GrazieBundle.message("specification.quick.fix.navigate.to.contradiction")
    override fun getName(): String = familyName
    override fun startInWriteAction(): Boolean = false
    override fun getIcon(flags: Int): Icon = AllIcons.Actions.Find

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      if (!file.isValid) return
      PsiNavigationSupport.getInstance().createNavigatable(project, file, offset).navigate(true)
    }
  }
}
