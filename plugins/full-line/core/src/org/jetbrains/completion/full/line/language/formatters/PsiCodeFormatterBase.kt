package org.jetbrains.completion.full.line.language.formatters

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.jetbrains.completion.full.line.language.PsiCodeFormatter

abstract class PsiCodeFormatterBase : PsiCodeFormatter {
  /**
   * Implementation should mark subtrees of input tree using [PsiElement.isHidden] for subtrees that need to be hidden and
   * return rollback prefix.
   * @param psiFile input file
   * @param position a special element with 'dummy identifier' in [psiFile] at caret position
   * @param offset where code completion was invoked.
   */
  protected abstract fun cutTree(psiFile: PsiFile, position: PsiElement, offset: Int): List<String>

  protected var PsiElement.isHidden: Boolean
    get() = getUserData(isHiddenKey) == true
    set(value) = putUserData(isHiddenKey, value)

  private val isHiddenKey = Key<Boolean>("isHidden")

  override fun format(psiFile: PsiFile, position: PsiElement, offset: Int): PsiCodeFormatter.FormatResult {
    clearMarks(psiFile)
    val rollbackSuffix = cutTree(psiFile, position, offset)
    val representation = getTreeRepresentation(psiFile)
    return PsiCodeFormatter.FormatResult(
      gson.toJson(representation),
      rollbackSuffix
    )
  }

  private fun clearMarks(psiFile: PsiFile) {
    SyntaxTraverser.psiTraverser(psiFile)
      .preOrderDfsTraversal()
      .forEach { it.isHidden = false }
  }

  private fun getTreeRepresentation(psiFile: PsiFile): TreeRepresentation {
    val nodeToId = collectNodeToId(psiFile)
    val nodeRepresentation = nodeToId.entries
      .sortedBy { it.value }
      .map { (node, _) ->
        val childrenIds = node.children.mapNotNull { child -> nodeToId[child] }
        val token = if (PsiTreeUtil.firstChild(node) != node) "<EMPTY>" else node.text
        NodeRepresentation(
          node.elementType.toString(),
          childrenIds.takeIf { children -> children.isNotEmpty() },
          token
        )
      }
    return TreeRepresentation("", nodeRepresentation)
  }

  private fun collectNodeToId(psiFile: PsiFile): Map<PsiElement, Int> {
    val nodeToId = hashMapOf<PsiElement, Int>()
    psiFile.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element.isHidden) {
          return
        }
        nodeToId[element] = nodeToId.size
        super.visitElement(element)
      }
    })
    return nodeToId
  }

  private val gson = GsonBuilder().apply {
    disableHtmlEscaping()
  }.create()

  private data class NodeRepresentation(
    val node: String,
    val children: List<Int>? = null,
    val token: String
  )

  private data class TreeRepresentation(
    val label: String,
    @SerializedName("AST") val tree: List<NodeRepresentation>
  )
}
