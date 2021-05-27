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

  private val rangeMap: HashMap<PsiFile, List<TextRange>> by lazy {
    val changeListManager = ChangeListManager.getInstance(myProject)
    val vcsFacade = VcsFacade.getInstance()
    val psiManager = PsiManager.getInstance(myProject)
    val psiFiles =
      (myGivenVirtualFiles ?: changeListManager.affectedFiles.toTypedArray())
        .mapNotNull { psiManager.findFile(it) }.toMutableList()

    val result = HashMap<PsiFile, List<TextRange>>()
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
          startOffset = CharArrayUtil.shiftForward(document.charsSequence, startOffset, endOffset, " /t")
          if (startOffset == endOffset) continue
          endOffset = CharArrayUtil.shiftBackward(document.charsSequence, startOffset, endOffset, " /t")
          val lineRange = TextRange(startOffset, endOffset)
          ranges.add(lineRange)
        }

        result[file] = ranges
      }
    }
    result
  }

  override fun getVirtualFiles(): Array<VirtualFile> = rangeMap.keys.map { it.virtualFile }.toTypedArray()

  private val psiElementsLazy: Array<PsiElement> by lazy {
    ReadAction.compute<Array<PsiElement>, RuntimeException> {
      val elements: List<PsiElement> = ArrayList()

      this.rangeMap.forEach { (psiFile, ranges) ->
        ranges.forEach { CollectPsiElementsAtRange(psiFile, elements, it.startOffset, it.endOffset) }
      }

      elements.toTypedArray()
    }
  }

  override fun getPsiElements(): Array<PsiElement> = psiElementsLazy

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VcsChangesLocalSearchScope) return false
    return myProject === other.myProject && myIgnoreInjectedPsi == other.myIgnoreInjectedPsi &&
           Arrays.equals(myGivenVirtualFiles, other.myGivenVirtualFiles)
  }

  override fun toString(): String {
    val builder = StringBuilder()
    builder.append("Vcs Changes Local Search Scope")
    if (myGivenVirtualFiles != null) {
      builder.append("{")
      var first = true
      for (file in myGivenVirtualFiles) {
        if (first) {
          builder.append(",")
          first = false
        }
        builder.append(file)
      }
    }
    return builder.toString()
  }

  override fun getWeight(): Int = 2

  override fun calcHashCode(): Int {
    var result = if (myIgnoreInjectedPsi) 27644437 else 1100101
    if (myGivenVirtualFiles != null) {
      for (file in myGivenVirtualFiles) {
        result = result * 397 xor file.hashCode()
      }
    }
    return result
  }

  override fun containsRange(file: PsiFile, range: TextRange): Boolean {
    return getRanges(file).any { it.contains(range) }
  }

  override fun getRanges(file: VirtualFile): Array<TextRange> {
    return ReadAction.compute<Array<TextRange>, RuntimeException> {
      PsiManager.getInstance(myProject).findFile(file)?.let { getRanges(it) } ?: TextRange.EMPTY_ARRAY
    }
  }

  private fun getRanges(psiFile: PsiFile): Array<TextRange> {
    return rangeMap[psiFile]?.toTypedArray() ?: TextRange.EMPTY_ARRAY
  }
}