package com.intellij.vcs.changes

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.ide.util.treeView.WeighedItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.scope.RangeBasedLocalSearchScope
import com.intellij.util.text.CharArrayUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import java.util.*

@Suppress("EqualsOrHashCode")
class VcsChangesLocalSearchScope(private val myProject: Project,
                                 displayName: @Nls String,
                                 private val myGivenVirtualFiles: Array<VirtualFile>?,
                                 ignoreInjectedPsi: Boolean) : RangeBasedLocalSearchScope(displayName, ignoreInjectedPsi), WeighedItem {

  companion object {
    private val logger = Logger.getInstance(VcsChangesLocalSearchScope::class.java)
  }

  private val rangeMap: HashMap<VirtualFile, List<TextRange>> by lazy {
    ReadAction.compute<HashMap<VirtualFile, List<TextRange>>, RuntimeException> {
      val changeListManager = ChangeListManager.getInstance(myProject)
      val vcsFacade = VcsFacade.getInstance()
      val psiManager = PsiManager.getInstance(myProject)
      val psiFiles =
        (myGivenVirtualFiles ?: changeListManager.affectedFiles.toTypedArray())
          .mapNotNull { psiManager.findFile(it) }.toMutableList()

      if (logger.isTraceEnabled)
        logger.trace("PSI files for VcsChangesLocalSearchScope: ${psiFiles.joinToString()}")

      val result = HashMap<VirtualFile, List<TextRange>>()
      for (file in psiFiles) {
        val info = vcsFacade.getChangedRangesInfo(file)
        val document = file.viewProvider.document
        if (info != null) {
          val ranges = ArrayList<TextRange>()
          if (logger.isTraceEnabled)
            logger.trace("Changed ranges for a file $file: ${info.allChangedRanges.joinToString()}")

          for (range in info.allChangedRanges) {
            val startLine = document.getLineNumber(range.startOffset)
            val endLine = document.getLineNumber(range.endOffset)
            var startOffset = document.getLineStartOffset(startLine)
            var endOffset = document.getLineEndOffset(endLine)
            startOffset = CharArrayUtil.shiftForward(document.charsSequence, startOffset, endOffset, " \t")
            if (startOffset == endOffset) continue
            endOffset = CharArrayUtil.shiftBackward(document.charsSequence, startOffset, endOffset, " \t")
            val lineRange = TextRange(startOffset, endOffset)
            ranges.add(lineRange)
          }

          result[file.virtualFile] = ranges
        }
        else {
          if (logger.isTraceEnabled)
            logger.trace("No changes for file $file")

          val virtualFile = file.virtualFile
          if (changeListManager.isUnversioned(virtualFile) && !changeListManager.isIgnoredFile(virtualFile)) {
            // Must be a new file, not yet added to VCS
            result[file.virtualFile] = listOf(TextRange(0, document.textLength))
          }
        }
      }
      result
    }
  }

  override fun getVirtualFiles(): Array<VirtualFile> = rangeMap.keys.toTypedArray()

  override fun getPsiElements(): Array<PsiElement> = ReadAction.compute<Array<PsiElement>, RuntimeException> {
    val elements: List<PsiElement> = ArrayList()
    val psiManager = PsiManager.getInstance(myProject)
    this.rangeMap.forEach { (virtualFile, ranges) ->
      val psiFile = psiManager.findFile(virtualFile)
      ranges.forEach { collectPsiElementsAtRange(psiFile, elements, it.startOffset, it.endOffset) }
    }

    elements.toTypedArray()
  }

  override fun equals(other: Any?): Boolean
    = this === other ||
      other is VcsChangesLocalSearchScope &&
      myProject === other.myProject && myIgnoreInjectedPsi == other.myIgnoreInjectedPsi &&
      Arrays.equals(myGivenVirtualFiles, other.myGivenVirtualFiles)

  override fun toString()
    = StringBuilder()
      .append("Vcs Changes Local Search Scope")
      .apply { myGivenVirtualFiles?.joinTo(this, prefix = "{", postfix = "}") { it.presentableName } }
      .toString()

  override fun getWeight(): Int = 2

  override fun calcHashCode(): Int = Objects.hash(myIgnoreInjectedPsi, Arrays.hashCode(myGivenVirtualFiles))

  override fun containsRange(file: PsiFile, range: TextRange): Boolean
    = getRanges(file.virtualFile).any { it.contains(range) }

  override fun getRanges(file: VirtualFile): Array<TextRange>
    = rangeMap[file]?.toTypedArray() ?: TextRange.EMPTY_ARRAY
}