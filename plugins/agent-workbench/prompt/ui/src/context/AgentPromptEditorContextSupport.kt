// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
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
private const val EDITOR_CONTEXT_FILE_ITEM_ID = "editor.file"
private const val EDITOR_CONTEXT_SYMBOL_ITEM_ID = "editor.symbol"
private const val EDITOR_CONTEXT_SNIPPET_ITEM_ID = "editor.snippet"

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

    val fileItem = snapshot.filePath
      ?.takeIf { it.isNotBlank() }
      ?.let { filePath ->
        AgentPromptContextItem(
          rendererId = AgentPromptContextRendererIds.FILE,
          title = AgentPromptBundle.message("context.file.title"),
          body = filePath,
          payload = AgentPromptPayload.obj("path" to AgentPromptPayload.str(filePath)),
          itemId = EDITOR_CONTEXT_FILE_ITEM_ID,
          source = "editor",
          truncation = AgentPromptContextTruncation.none(filePath.length),
        )
      }
    if (fileItem != null) {
      items += fileItem
    }

    val snippet = snapshot.snippet
    val snippetTitleKey = if (snippet.fromSelection) {
      "context.snippet.selection.title"
    }
    else {
      "context.snippet.caret.title"
    }
    val snippetPayloadFields = linkedMapOf(
      "startLine" to AgentPromptPayload.num(snippet.startLine),
      "endLine" to AgentPromptPayload.num(snippet.endLine),
      "selection" to AgentPromptPayload.bool(snippet.fromSelection),
    )
    snapshot.language?.takeIf { it.isNotBlank() }?.let { snippetPayloadFields["language"] = AgentPromptPayload.str(it) }
    val symbolName = normalizeSymbolName(snapshot.symbolName)
    if (symbolName != null) {
      items += AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.SYMBOL,
        title = AgentPromptBundle.message("context.symbol.title"),
        body = symbolName,
        payload = AgentPromptPayload.obj("symbol" to AgentPromptPayload.str(symbolName)),
        itemId = EDITOR_CONTEXT_SYMBOL_ITEM_ID,
        parentItemId = fileItem?.itemId,
        source = "editor",
        truncation = AgentPromptContextTruncation.none(symbolName.length),
      )
    }

    val snippetItem = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = AgentPromptBundle.message(snippetTitleKey, snippet.startLine, snippet.endLine),
      body = snippet.text,
      payload = AgentPromptPayloadValue.Obj(snippetPayloadFields),
      itemId = EDITOR_CONTEXT_SNIPPET_ITEM_ID,
      parentItemId = fileItem?.itemId,
      source = "editor",
      truncation = AgentPromptContextTruncation(
        originalChars = snippet.originalChars,
        includedChars = snippet.includedChars,
        reason = snippet.truncationReason,
      ),
    )
    items += snippetItem

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
    val language = resolveLanguageName(psiFile)
    return AgentEditorContextSnapshot(
      filePath = filePath,
      language = language,
      snippet = snippet,
      symbolName = symbolName,
    )
  }

  private fun resolveLanguageName(psiFile: PsiFile?): String? {
    return runReadActionBlocking {
      psiFile?.language?.id?.takeIf { it.isNotBlank() }
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
        val name = normalizeSymbolName(namedElement?.name)
        if (name != null) {
          return@runReadActionBlocking name
        }
        element = element.parent
      }
      null
    }
  }

  private fun normalizeSymbolName(rawName: String?): String? {
    val normalized = rawName
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: return null
    if (isPlaceholderSymbolName(normalized)) {
      return null
    }
    return normalized
  }

  private fun isPlaceholderSymbolName(name: String): Boolean {
    return name.length >= 2 && name.first() == '<' && name.last() == '>'
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
      val truncateOutcome = truncate(text)
      return AgentPromptSnippet(
        text = truncateOutcome.text,
        startLine = startLine,
        endLine = endLine,
        fromSelection = true,
        originalChars = truncateOutcome.originalChars,
        includedChars = truncateOutcome.includedChars,
        truncated = truncateOutcome.truncated,
        truncationReason = truncateOutcome.reason,
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
    val truncateOutcome = truncate(text)
    return AgentPromptSnippet(
      text = truncateOutcome.text,
      startLine = startLineIndex + 1,
      endLine = endLineIndex + 1,
      fromSelection = false,
      originalChars = truncateOutcome.originalChars,
      includedChars = truncateOutcome.includedChars,
      truncated = truncateOutcome.truncated,
      truncationReason = truncateOutcome.reason,
    )
  }

  private fun truncate(text: String): TruncateOutcome {
    val originalChars = text.length
    if (text.length <= MAX_SNIPPET_CHARS) {
      return TruncateOutcome(
        text = text,
        originalChars = originalChars,
        includedChars = originalChars,
        truncated = false,
        reason = AgentPromptContextTruncationReason.NONE,
      )
    }
    val truncatedText = buildString(MAX_SNIPPET_CHARS + 32) {
      append(text, 0, MAX_SNIPPET_CHARS)
      append("\n...[truncated]")
    }
    return TruncateOutcome(
      text = truncatedText,
      originalChars = originalChars,
      includedChars = truncatedText.length,
      truncated = true,
      reason = AgentPromptContextTruncationReason.SOURCE_LIMIT,
    )
  }
}

private data class TruncateOutcome(
  @JvmField val text: String,
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val truncated: Boolean,
  @JvmField val reason: AgentPromptContextTruncationReason,
)

internal data class AgentEditorContextSnapshot(
  @JvmField val filePath: String?,
  @JvmField val language: String?,
  @JvmField val snippet: AgentPromptSnippet,
  @JvmField val symbolName: String?,
)

internal data class AgentPromptSnippet(
  @JvmField val text: String,
  @JvmField val startLine: Int,
  @JvmField val endLine: Int,
  @JvmField val fromSelection: Boolean,
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val truncated: Boolean,
  @JvmField val truncationReason: AgentPromptContextTruncationReason,
)
