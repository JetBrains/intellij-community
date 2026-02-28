// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import kotlin.math.max
import kotlin.math.min

private const val MAX_SNIPPET_CHARS = 2_000
private const val CARET_CONTEXT_LINE_RADIUS = 3

internal object AgentPromptEditorContextSupport {
  fun buildSnapshotFromInvocation(invocationData: AgentPromptInvocationData): AgentEditorContextSnapshot? {
    val project = invocationData.project
    val dataContext = invocationData.dataContextOrNull() ?: return null
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
    val document = editor.document
    val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext)
      ?: PsiDocumentManager.getInstance(project).getPsiFile(document)
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
      ?: FileDocumentManager.getInstance().getFile(document)
      ?: psiFile?.virtualFile
    return buildSnapshot(editor = editor, psiFile = psiFile, virtualFile = virtualFile)
  }

  fun buildSnapshotFromSelectedEditor(project: Project): AgentEditorContextSnapshot? {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
      ?: psiFile?.virtualFile
    return buildSnapshot(editor = editor, psiFile = psiFile, virtualFile = virtualFile)
  }

  fun buildContextItems(snapshot: AgentEditorContextSnapshot): List<AgentPromptContextItem> {
    val items = ArrayList<AgentPromptContextItem>(3)
    val snippet = snapshot.snippet
    val snippetTitleKey = if (snippet.fromSelection) {
      "context.snippet.selection.title"
    }
    else {
      "context.snippet.caret.title"
    }
    items += AgentPromptContextItem(
      kindId = AgentPromptContextKinds.SNIPPET,
      title = AgentPromptBundle.message(snippetTitleKey, snippet.startLine, snippet.endLine),
      content = snippet.text,
      metadata = mapOf(
        "startLine" to snippet.startLine.toString(),
        "endLine" to snippet.endLine.toString(),
        "selection" to snippet.fromSelection.toString(),
      ),
    )

    if (!snapshot.filePath.isNullOrBlank()) {
      val metadata = LinkedHashMap<String, String>()
      snapshot.language?.let { metadata["language"] = it }
      items += AgentPromptContextItem(
        kindId = AgentPromptContextKinds.FILE,
        title = AgentPromptBundle.message("context.file.title"),
        content = snapshot.filePath,
        metadata = metadata,
      )
    }

    if (!snapshot.symbolName.isNullOrBlank()) {
      items += AgentPromptContextItem(
        kindId = AgentPromptContextKinds.SYMBOL,
        title = AgentPromptBundle.message("context.symbol.title"),
        content = snapshot.symbolName,
      )
    }

    return items
  }

  private fun buildSnapshot(
    editor: Editor,
    psiFile: PsiFile?,
    virtualFile: VirtualFile?,
  ): AgentEditorContextSnapshot? {
    val snippet = extractSnippet(editor) ?: return null
    val symbolName = resolveSymbolName(psiFile, editor)
    val filePath = virtualFile?.path ?: psiFile?.virtualFile?.path
    val language = resolveLanguageName(psiFile, virtualFile)
    return AgentEditorContextSnapshot(
      filePath = filePath,
      language = language,
      snippet = snippet,
      symbolName = symbolName,
    )
  }

  private fun resolveLanguageName(psiFile: PsiFile?, virtualFile: VirtualFile?): String? {
    return runReadActionBlocking {
      val psiLanguage = psiFile?.language?.displayName?.takeIf { it.isNotBlank() }
      if (psiLanguage != null) {
        return@runReadActionBlocking psiLanguage
      }
      virtualFile?.fileType?.name?.takeIf { it.isNotBlank() }
    }
  }

  private fun resolveSymbolName(psiFile: PsiFile?, editor: Editor): String? {
    return runReadActionBlocking {
      val file = psiFile ?: return@runReadActionBlocking null
      val maxOffset = max(0, file.textLength - 1)
      val offset = min(max(0, editor.caretModel.offset), maxOffset)
      var element = file.findElementAt(offset)
      while (element != null) {
        val namedElement = element as? PsiNamedElement
        val name = namedElement?.name
        if (!name.isNullOrBlank()) {
          return@runReadActionBlocking name
        }
        element = element.parent
      }
      null
    }
  }

  private fun extractSnippet(editor: Editor): AgentPromptSnippet? {
    val document = editor.document
    val selectionModel = editor.selectionModel
    if (selectionModel.hasSelection()) {
      val startOffset = min(selectionModel.selectionStart, selectionModel.selectionEnd)
      val endOffsetExclusive = max(selectionModel.selectionStart, selectionModel.selectionEnd)
      val safeEndOffsetExclusive = max(startOffset + 1, endOffsetExclusive)
      val startLine = document.getLineNumber(startOffset) + 1
      val endLine = document.getLineNumber(safeEndOffsetExclusive - 1) + 1
      val text = document.getText(TextRange(startOffset, safeEndOffsetExclusive))
      return AgentPromptSnippet(
        text = truncate(text),
        startLine = startLine,
        endLine = endLine,
        fromSelection = true,
      )
    }

    val lineCount = document.lineCount
    if (lineCount <= 0) {
      return null
    }
    val caretLine = document.getLineNumber(editor.caretModel.offset)
    val startLineIndex = max(0, caretLine - CARET_CONTEXT_LINE_RADIUS)
    val endLineIndex = min(lineCount - 1, caretLine + CARET_CONTEXT_LINE_RADIUS)
    val startOffset = document.getLineStartOffset(startLineIndex)
    val endOffset = document.getLineEndOffset(endLineIndex)
    if (endOffset <= startOffset) {
      return null
    }
    val text = document.getText(TextRange(startOffset, endOffset))
    return AgentPromptSnippet(
      text = truncate(text),
      startLine = startLineIndex + 1,
      endLine = endLineIndex + 1,
      fromSelection = false,
    )
  }

  private fun truncate(text: String): String {
    if (text.length <= MAX_SNIPPET_CHARS) {
      return text
    }
    return buildString(MAX_SNIPPET_CHARS + 32) {
      append(text, 0, MAX_SNIPPET_CHARS)
      append("\n...[truncated]")
    }
  }
}

internal data class AgentEditorContextSnapshot(
  val filePath: String?,
  val language: String?,
  val snippet: AgentPromptSnippet,
  val symbolName: String?,
)

internal data class AgentPromptSnippet(
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val fromSelection: Boolean,
)
