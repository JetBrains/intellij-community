package com.intellij.grazie.markdown.specification.inspection

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
import com.intellij.grazie.markdown.specification.inspection.quickfix.SpecificationReplacementQuickFix
import com.intellij.grazie.markdown.specification.search.SpecificationFileSelector
import com.intellij.grazie.markdown.specification.search.SpecificationFileSelector.MAX_FILES_PER_SEARCH_TERM
import com.intellij.grazie.markdown.specification.utils.SpecificationUtils.getSkillFiles
import com.intellij.grazie.markdown.specification.utils.SpecificationUtils.getSpecificationLikePattern
import com.intellij.grazie.markdown.specification.utils.SpecificationUtils.isSkillFile
import com.intellij.grazie.markdown.specification.utils.SpecificationUtils.isSpecificationLikeFile
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.markdown.backend.services.MarkdownFileGraphUtils
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import com.intellij.util.gist.GistManager
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import org.jetbrains.annotations.VisibleForTesting
import java.io.DataInput
import java.io.DataOutput
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

private val specificationDependenciesKey =
  Key.create<CachedValue<ConcurrentHashMap<VirtualFile, Set<VirtualFile>>>>("grazie.specification.dependencies")

/**
 * The term frequencies of a specification file.
 * Increment the version when [SpecificationFileSelector.buildTermFrequencies] changes, because the gist keeps the data on the disk.
 */
private val termFrequenciesGist by lazy {
  GistManager.getInstance().newPsiFileGist(
    "grazie.markdown.specification.terms",
    2,
    TermFrequenciesExternalizer,
  ) { psiFile -> SpecificationFileSelector.buildTermFrequencies(psiFile.text) }
}

private object TermFrequenciesExternalizer : DataExternalizer<Map<String, Int>> {
  override fun save(out: DataOutput, value: Map<String, Int>) {
    DataInputOutputUtil.writeINT(out, value.size)
    for ((term, frequency) in value) {
      IOUtil.writeUTF(out, term)
      DataInputOutputUtil.writeINT(out, frequency)
    }
  }

  override fun read(input: DataInput): Map<String, Int> {
    val size = DataInputOutputUtil.readINT(input)
    val frequencies = HashMap<String, Int>(size)
    repeat(size) {
      frequencies[IOUtil.readUTF(input)] = DataInputOutputUtil.readINT(input)
    }
    return frequencies
  }
}

private data class CandidateSearchResult(
  val files: List<VirtualFile>,
  val documentFrequencies: Map<String, Int>,
)

/** The files that contain a term. [files] is empty when the term is not rare. */
private data class TermSearchResult(val documentFrequency: Int, val files: Collection<VirtualFile>)

/** Finds the specification files that contain a term. It keeps the result for each term. */
private class TermSearcher(project: Project) {
  private val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
    GlobalSearchScope.projectScope(project),
    MarkdownFileType.INSTANCE,
  )
  private val specificationPattern = getSpecificationLikePattern()
  private val searchHelper = PsiSearchHelper.getInstance(project)
  private val results = HashMap<String, TermSearchResult>()

  fun search(term: String): TermSearchResult = results.getOrPut(term) {
    val files = LinkedHashSet<VirtualFile>()
    var documentFrequency = 0
    val isRare = searchHelper.processCandidateFilesForText(scope, UsageSearchContext.ANY, false, term) { file ->
      if (isDependency(file, specificationPattern)) {
        if (++documentFrequency > MAX_FILES_PER_SEARCH_TERM) return@processCandidateFilesForText false
        files.add(file)
      }
      true
    }
    TermSearchResult(documentFrequency, if (isRare) files else emptySet())
  }
}

private fun isDependency(file: PsiFile): Boolean {
  if (!file.language.isKindOf(MarkdownLanguage.INSTANCE)) return false
  return isSpecificationLikeFile(file) || isSkillFile(file)
}

private fun isDependency(file: VirtualFile, specificationPattern: Regex?): Boolean =
  isSpecificationLikeFile(file, specificationPattern) || isSkillFile(file)

@VisibleForTesting
open class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {
  private companion object {
    /** A file with more linked files than this does not get similar files. */
    private const val MAX_LINKED_FILES = 20
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Contradiction> = ContradictionAnalyzer()
  override fun getDependencies(root: PsiFile): Set<PsiFile> = getAndCacheDependencies(root)
    .asSequence()
    .mapNotNull { root.manager.findFile(it) }
    .filter { isDependency(it) }
    .toSet()

  private fun getAndCacheDependencies(root: PsiFile): Set<VirtualFile> {
    val project = root.project
    val rootFile = root.virtualFile ?: throw IllegalStateException("Virtual file for a file was not found")
    val cache = CachedValuesManager.getManager(project).getCachedValue(
      project,
      specificationDependenciesKey,
      {
        CachedValueProvider.Result.create(
          ConcurrentHashMap<VirtualFile, Set<VirtualFile>>(),
          PsiModificationTracker.getInstance(project).forLanguages { it.isKindOf(MarkdownLanguage.INSTANCE) },
          VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
        )
      },
      false,
    )

    cache[rootFile]?.let { return it }
    val files = collectDependencies(root)
    for (file in files) {
      cache.putIfAbsent(file, files)
    }
    return files
  }

  /**
   * Returns the connected component of [root].
   * Two files are connected when one links to the other, when they belong to one skill, or when they are similar.
   * The component does not depend on the start file, so every file in it gets the same set.
   */
  private fun collectDependencies(root: PsiFile): Set<VirtualFile> {
    val project = root.project
    val psiManager = PsiManager.getInstance(project)
    val linkedFiles = HashMap<VirtualFile, Set<VirtualFile>>()
    fun getLinkedFiles(file: PsiFile): Set<VirtualFile> = linkedFiles.getOrPut(file.virtualFile) {
      val linked = MarkdownFileGraphUtils.getDependencySet(file) { isDependency(it) }
      linked.forEach { linkedFiles[it] = linked }
      linked
    }
    fun hasFewLinkedFiles(file: VirtualFile): Boolean {
      val psiFile = psiManager.findFile(file) ?: return false
      return getLinkedFiles(psiFile).size <= MAX_LINKED_FILES
    }

    val termSearcher = TermSearcher(project)
    val result = LinkedHashSet<VirtualFile>()
    val queue = ArrayDeque<VirtualFile>()
    fun enqueue(files: Collection<VirtualFile>) {
      files.filter { result.add(it) }.forEach { queue.add(it) }
    }

    enqueue(listOf(root.virtualFile))
    while (queue.isNotEmpty()) {
      ProgressManager.checkCanceled()
      val file = queue.removeFirst()
      val psiFile = psiManager.findFile(file) ?: continue
      enqueue(getLinkedFiles(psiFile))
      enqueue(getSkillFiles(file))
      if (hasFewLinkedFiles(file)) {
        enqueue(findSimilarFiles(psiFile, termSearcher).filter(::hasFewLinkedFiles))
      }
    }
    return result
  }

  private fun findSimilarFiles(root: PsiFile, termSearcher: TermSearcher): List<VirtualFile> {
    val project = root.project
    val rootFile = root.virtualFile ?: return emptyList()
    val indexedRoot = getIndexedDocument(project, rootFile) ?: return emptyList()
    val searchResult = findCandidateFiles(indexedRoot, termSearcher)
    val indexedCandidates = searchResult.files
      .asSequence()
      .filter { it != rootFile }
      .mapNotNull { getIndexedDocument(project, it) }
      .toList()
    val similar = SpecificationFileSelector.findSimilar(
      root = indexedRoot,
      candidates = indexedCandidates,
      documentFrequencies = searchResult.documentFrequencies,
    )
    val filesByPath = searchResult.files.associateBy { it.path }
    return similar.mapNotNull { filesByPath[it.path] }
  }

  private fun findCandidateFiles(
    indexedRoot: SpecificationFileSelector.IndexedDocument,
    termSearcher: TermSearcher,
  ): CandidateSearchResult {
    val candidates = LinkedHashSet<VirtualFile>()
    val documentFrequencies = HashMap<String, Int>()
    for (term in indexedRoot.terms.keys) {
      val search = termSearcher.search(term)
      documentFrequencies[term] = search.documentFrequency
      candidates.addAll(search.files)
    }
    return CandidateSearchResult(candidates.toList(), documentFrequencies)
  }

  /** The gist keeps only the terms. The path comes from the file, because a rename does not change the file content. */
  private fun getIndexedDocument(project: Project, file: VirtualFile): SpecificationFileSelector.IndexedDocument? {
    if (!file.isValid) return null
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val terms = termFrequenciesGist.getFileData(psiFile) ?: return null
    return SpecificationFileSelector.IndexedDocument(file.path, terms)
  }

  override fun reportProblems(holder: ProblemsHolder, file: PsiFile, dependencies: Set<PsiFile>, issues: List<LlmIssue<Contradiction>>) {
    val filePaths = dependencies.associateBy { it.viewProvider.virtualFile.path }
    issues.forEach { reportIssue(holder, file, it, filePaths) }
  }

  private fun reportIssue(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<Contradiction>, filePaths: Map<String, PsiFile>) {
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
          contradictionFile.viewProvider.virtualFile,
          contradiction.startOffset(),
          StringUtil.offsetToLineNumber(contradictionFile.text, contradiction.startOffset()) + 1,
          contradiction.path() == filePath,
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
    private val lineNumber: Int,
    private val sameFile: Boolean,
  ) : LocalQuickFix, Iconable {
    override fun getName(): String {
      if (sameFile) {
        return GrazieBundle.message("specification.quick.fix.navigate.to.contradiction.same.file", lineNumber)
      }
      return GrazieBundle.message("specification.quick.fix.navigate.to.contradiction.another.file", file.name, lineNumber)
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
