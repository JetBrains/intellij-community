package com.intellij.vcs.changes

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.ide.util.treeView.WeighedItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.scope.RangeBasedLocalSearchScope
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.Nls
import java.util.*

@Suppress("EqualsOrHashCode")
class VcsChangesLocalSearchScope(private val myProject: Project,
                                 displayName: @Nls String,
                                 private val myGivenVirtualFiles: Array<VirtualFile>?,
                                 ignoreInjectedPsi: Boolean) : RangeBasedLocalSearchScope(displayName, ignoreInjectedPsi), WeighedItem {

  private val rangeMap: HashMap<VirtualFile, List<TextRange>> by lazy {
    ReadAction.compute<HashMap<VirtualFile, List<TextRange>>, RuntimeException> {
      val changeListManager = ChangeListManager.getInstance(myProject)
      val vcsFacade = VcsFacade.getInstance()
      val psiManager = PsiManager.getInstance(myProject)
      val psiFiles =
        (myGivenVirtualFiles ?: changeListManager.affectedFiles.toTypedArray())
          .mapNotNull { psiManager.findFile(it) }.toMutableList()

      val result = HashMap<VirtualFile, List<TextRange>>()
      for (file in psiFiles) {
        val info = vcsFacade.getChangedRangesInfo(file)
        if (info != null) {
          val document = file.viewProvider.document
          val ranges = ArrayList<TextRange>()

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