package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.Replacement
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.language.markdown.semantics.inspection.quickfix.SpecificationReplacementQuickFix
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.markdown.backend.services.MarkdownFileGraphUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@VisibleForTesting
open class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {
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

  override fun reportProblems(holder: ProblemsHolder, file: PsiFile, dependencies: Set<PsiFile>, issues: List<LlmIssue<Contradiction>>) {
    val filePaths = dependencies.asSequence()
      .map { it.viewProvider.virtualFile }
      .associateBy { it.path }
    issues.forEach { reportIssue(holder, file, it, filePaths) }
  }

  private fun reportIssue(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<Contradiction>, filePaths: Map<String, VirtualFile>) {
    val filePath = file.viewProvider.virtualFile.path
    issue.issue.statements.filter { it.path == filePath }.forEach { statement ->
      val range = TextRange(statement.startOffset, statement.endOffset)
      val underline = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

      val fixes = mutableListOf<LocalQuickFix>()
      fixes.addAll(
        SpecificationReplacementQuickFix(underline, statement.replacements.map { Replacement(it) }).getAllAsFixes()
      )

      statement.contradicts().forEach { contradiction ->
        val contradictionFile = filePaths[contradiction.path()] ?: return@forEach
        fixes.add(NavigateToContradictionQuickFix(
          contradictionFile, contradiction.startOffset(), contradiction.path() == filePath
        ))
      }

      holder.registerProblem(ProblemDescriptorBase(
        file, file, statement.suggestion, fixes.toTypedArray(),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        false, TextRange(statement.startOffset(), statement.endOffset()), true, true
      ))
    }
  }

  private class NavigateToContradictionQuickFix(
    private val file: VirtualFile,
    private val offset: Int,
    private val sameFile: Boolean,
  ) : LocalQuickFix, Iconable {
    override fun getName(): String {
      if (sameFile) {
        return GrazieBundle.message("specification.quick.fix.navigate.to.contradiction.same.file", offset)
      }
      return GrazieBundle.message("specification.quick.fix.navigate.to.contradiction.another.file", file.name, offset)
    }
    override fun getFamilyName(): String = GrazieBundle.message("specification.quick.fix.navigate.to.contradiction.family")
    override fun startInWriteAction(): Boolean = false
    override fun getIcon(flags: Int): Icon = AllIcons.Actions.Find

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      if (!file.isValid) return
      PsiNavigationSupport.getInstance().createNavigatable(project, file, offset).navigate(true)
    }
  }
}
