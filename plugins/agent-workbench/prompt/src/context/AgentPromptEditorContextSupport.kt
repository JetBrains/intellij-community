// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextMetadataKeys
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReasons
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
    val snippetMetadata = linkedMapOf(
      "startLine" to snippet.startLine.toString(),
      "endLine" to snippet.endLine.toString(),
      "selection" to snippet.fromSelection.toString(),
      AgentPromptContextMetadataKeys.SOURCE to "editor",
      AgentPromptContextMetadataKeys.ORIGINAL_CHARS to snippet.originalChars.toString(),
      AgentPromptContextMetadataKeys.INCLUDED_CHARS to snippet.includedChars.toString(),
      AgentPromptContextMetadataKeys.TRUNCATED to snippet.truncated.toString(),
      AgentPromptContextMetadataKeys.TRUNCATION_REASON to snippet.truncationReason,
    )
    snapshot.language?.takeIf { it.isNotBlank() }?.let { snippetMetadata[AgentPromptContextMetadataKeys.LANGUAGE] = it }
    items += AgentPromptContextItem(
      kindId = AgentPromptContextKinds.SNIPPET,
      title = AgentPromptBundle.message(snippetTitleKey, snippet.startLine, snippet.endLine),
      content = snippet.text,
      metadata = snippetMetadata,
    )

    if (!snapshot.filePath.isNullOrBlank()) {
      val filePath = snapshot.filePath
      val metadata = linkedMapOf(
        AgentPromptContextMetadataKeys.SOURCE to "editor",
        AgentPromptContextMetadataKeys.ORIGINAL_CHARS to filePath.length.toString(),
        AgentPromptContextMetadataKeys.INCLUDED_CHARS to filePath.length.toString(),
        AgentPromptContextMetadataKeys.TRUNCATED to false.toString(),
        AgentPromptContextMetadataKeys.TRUNCATION_REASON to AgentPromptContextTruncationReasons.NONE,
      )
      items += AgentPromptContextItem(
        kindId = AgentPromptContextKinds.FILE,
        title = AgentPromptBundle.message("context.file.title"),
        content = filePath,
        metadata = metadata,
      )
    }

    if (!snapshot.symbolName.isNullOrBlank()) {
      val symbolName = snapshot.symbolName
      items += AgentPromptContextItem(
        kindId = AgentPromptContextKinds.SYMBOL,
        title = AgentPromptBundle.message("context.symbol.title"),
        content = symbolName,
        metadata = linkedMapOf(
          AgentPromptContextMetadataKeys.SOURCE to "editor",
          AgentPromptContextMetadataKeys.ORIGINAL_CHARS to symbolName.length.toString(),
          AgentPromptContextMetadataKeys.INCLUDED_CHARS to symbolName.length.toString(),
          AgentPromptContextMetadataKeys.TRUNCATED to false.toString(),
          AgentPromptContextMetadataKeys.TRUNCATION_REASON to AgentPromptContextTruncationReasons.NONE,
        ),
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
        reason = AgentPromptContextTruncationReasons.NONE,
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
      reason = AgentPromptContextTruncationReasons.SOURCE_LIMIT,
    )
  }
}

private data class TruncateOutcome(
  @JvmField val text: String,
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val truncated: Boolean,
  @JvmField val reason: String,
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
  @JvmField val truncationReason: String,
)
