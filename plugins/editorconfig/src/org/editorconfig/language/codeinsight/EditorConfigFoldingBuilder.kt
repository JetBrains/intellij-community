// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import kotlin.math.min

private val LOG = logger<EditorConfigFoldingBuilder>()

internal class EditorConfigFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    if (root !is EditorConfigPsiFile) {
      LOG.warn("Folding builder was given unexpected element")
      return emptyArray()
    }

    val descriptors = mutableListOf<FoldingDescriptor>()
    fun add(start: Int, end: Int, element: PsiElement) {
      val range = TextRange(start, end)
      val descriptor = FoldingDescriptor(element, range)
      descriptors.add(descriptor)
    }

    fun findCommentFoldingInChildren(element: PsiElement) {
      var firstComment: PsiComment? = null
      var start = 0
      var end = 0

      var child = element.firstChild

      while (child != null) {
        if (child is PsiComment) {
          if (firstComment == null) {
            start = child.textRange.startOffset
            firstComment = child
          }
          end = child.textRange.endOffset

          val next = child.nextSibling
          if (isLineBreak(next)) {
            child = next
          }
          else {
            add(start, end, firstComment)
            firstComment = null
          }
        }
        else {
          if (firstComment != null) {
            add(start, end, firstComment)
            firstComment = null
          }
          findCommentFoldingInChildren(child)
        }
        child = child.nextSibling
      }
      if (firstComment != null) {
        add(start, end, firstComment)
        // firstComment = null
      }
    }

    fun findSectionFoldingInElement(element: PsiElement) {
      when (element) {
        is EditorConfigSection -> {
          val start = element.optionList.firstOrNull()?.textRange?.startOffset ?: return
          val end = element.optionList.lastOrNull()?.textRange?.endOffset ?: return
          val range = TextRange(start, end)
          val descriptor = FoldingDescriptor(element, range)
          descriptors.add(descriptor)
        }

        else -> element.children.forEach(::findSectionFoldingInElement)
      }
    }

    findCommentFoldingInChildren(root)
    findSectionFoldingInElement(root)
    return descriptors.toTypedArray()
  }

  private val COMMENT_FOLD_LENGTH_LIMIT = 40

  override fun getPlaceholderText(node: ASTNode) = when (node.elementType) {
    EditorConfigElementTypes.LINE_COMMENT -> "${node.text.substring(0 until min(node.textLength, COMMENT_FOLD_LENGTH_LIMIT))}..."
    EditorConfigElementTypes.SECTION -> "..."
    else -> {
      LOG.warn("Requested folding placeholder for unknown node (${node.elementType})")
      "..."
    }
  }

  override fun isCollapsedByDefault(node: ASTNode) = false

  private fun isLineBreak(element: PsiElement?) =
    element != null && LINE_BREAK.matches(element.text)

  private val LINE_BREAK = "\\R".toRegex()
}
