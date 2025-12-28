// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.hotreload

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtTokens
import java.util.function.Consumer

private const val tipPrefix: String = "//TIP"
private const val MAGIC_SANDBOX_FILENAME = "ComposeSandbox.kt"

internal class DevkitHotReloadTips : com.intellij.lang.documentation.DocumentationProvider {

  private val commentTokenType: IElementType = KtTokens.EOL_COMMENT

  private fun isEnabledForFile(file: PsiFile): Boolean {
    if (!isIntelliJPlatformProject(file.project)) return false

    val filePath = file.virtualFile?.path ?: return false
    return filePath.endsWith(MAGIC_SANDBOX_FILENAME)
  }

  override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
    if (!isEnabledForFile(file)) return

    val visitedComments = mutableSetOf<PsiElement>()

    file.accept(object : PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (visitedComments.contains(comment)) return
        if (comment.node.elementType != commentTokenType) return

        if (comment.text.startsWith(tipPrefix)) {
          val wrapper = createTipComment(comment, visitedComments, commentTokenType)
          sink.accept(wrapper)
        }
      }
    })
  }

  override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
    if (isEnabledForFile(file)) return null

    var result: PsiDocCommentBase? = null
    file.accept(object: PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (comment.textRange.startOffset != range.startOffset) return

        result = TipComment(comment.parent, range, commentTokenType)
      }
    })

    return result
  }

  override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
    if (comment !is TipComment) return null

    @Suppress("HardCodedStringLiteral")
    val result = comment.text
      .split("\n")
      .map { it.trim() }
      .map { if (it.startsWith(tipPrefix)) it.substring(tipPrefix.length, it.length) else it }
      .map { if (it.startsWith("//")) it.substring(2, it.length) else it }
      .joinToString(separator = " ")
      .trim()

    @Suppress("HardCodedStringLiteral")
    return "<p>$result"
  }

  private fun createTipComment(start: PsiComment, visitedComments: MutableSet<PsiElement>, commentTokenType: IElementType): TipComment {
    var current: PsiElement = start
    while(true) {
      var nextSibling = current.nextSibling
      while (nextSibling is PsiWhiteSpace) nextSibling = nextSibling.nextSibling
      if (nextSibling?.node?.elementType != commentTokenType) break
      visitedComments.add(nextSibling)
      current = nextSibling
    }
    return TipComment(current.parent, TextRange(start.textRange.startOffset, current.textRange.endOffset), commentTokenType)
  }

  private class TipComment(
    private val parent: PsiElement,
    private val range: TextRange,
    private val commentTokenType: IElementType
  ): FakePsiElement(), PsiDocCommentBase {
    override fun getParent() = parent
    override fun getTokenType(): IElementType = commentTokenType

    override fun getTextRange() = range
    override fun getText() = range.substring(parent.containingFile.text)
    override fun getOwner() = parent
  }
}

internal class ToggleComposeSandboxTipsEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return

    if (!isIntelliJPlatformProject(project)) return

    val doc = editor.getDocument()
    val virtualFile = FileDocumentManager.getInstance().getFile(doc)

    if (virtualFile?.name == MAGIC_SANDBOX_FILENAME) {
      DocRenderManager.setDocRenderingEnabled(editor, true)
    }
  }
}