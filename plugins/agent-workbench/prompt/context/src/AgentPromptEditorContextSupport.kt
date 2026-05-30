// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ReferenceRange
import com.intellij.psi.util.PsiUtilCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
const val AGENT_PROMPT_MAX_SNIPPET_CHARS: Int = 2_000

private const val CARET_CONTEXT_LINE_RADIUS = 3
private const val EDITOR_CONTEXT_FILE_ITEM_ID = "editor.file"
private const val EDITOR_CONTEXT_SYMBOL_ITEM_ID = "editor.symbol"
private const val EDITOR_CONTEXT_SNIPPET_ITEM_ID = "editor.snippet"

@ApiStatus.Internal
object AgentPromptEditorContextSupport {
    fun buildSnapshotFromInvocation(invocationData: AgentPromptInvocationData): AgentEditorContextSnapshot? {
        val project = invocationData.project
        val dataContext = invocationData.dataContextOrNull() ?: return null
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
        val document = editor.document
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext)
            ?: runReadActionBlocking { PsiDocumentManager.getInstance(project).getPsiFile(document) }
        val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
            ?: FileDocumentManager.getInstance().getFile(document)
            ?: psiFile?.virtualFile
        val editorState = captureEditorState(project = project, editor = editor, virtualFile = virtualFile, psiFile = psiFile)
        return runReadActionBlocking { buildSnapshot(editorState) }
    }

    fun buildSnapshotFromSelectedEditor(project: Project): AgentEditorContextSnapshot? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        val editorState = captureEditorState(project = project, editor = editor, virtualFile = virtualFile, psiFile = null)
        return runReadActionBlocking { buildSnapshot(editorState) }
    }

    suspend fun collectSelectedEditorSnapshot(project: Project): AgentEditorContextSnapshot? {
        val editorState = withContext(Dispatchers.EDT) {
            if (project.isDisposed) {
                return@withContext null
            }
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@withContext null
            val document = editor.document
            val virtualFile = FileDocumentManager.getInstance().getFile(document)
            captureEditorState(project = project, editor = editor, virtualFile = virtualFile, psiFile = null)
        } ?: return null
        return readAction { buildSnapshot(editorState) }
    }

    fun buildContextItems(snapshot: AgentEditorContextSnapshot): List<AgentPromptContextItem> {
        val items = ArrayList<AgentPromptContextItem>(3)

        val fileItem = snapshot.filePath
            ?.takeIf { it.isNotBlank() }
            ?.let { filePath ->
                AgentPromptContextItem(
                    rendererId = AgentPromptContextRendererIds.FILE,
                    title = AgentPromptContextBundle.message("context.file.title"),
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
        } else {
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
                title = AgentPromptContextBundle.message("context.symbol.title"),
                body = symbolName,
                payload = AgentPromptPayload.obj("symbol" to AgentPromptPayload.str(symbolName)),
                itemId = EDITOR_CONTEXT_SYMBOL_ITEM_ID,
                parentItemId = fileItem?.itemId,
                source = "editor",
                truncation = AgentPromptContextTruncation.none(symbolName.length),
            )
        }

        items += AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = AgentPromptContextBundle.message(snippetTitleKey, snippet.startLine, snippet.endLine),
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

        return items
    }

    private fun captureEditorState(
        project: Project,
        editor: Editor,
        virtualFile: VirtualFile?,
        psiFile: PsiFile?,
    ): EditorState {
        val document = editor.document
        val selections = editor.caretModel.allCarets
            .asSequence()
            .filter { caret -> caret.hasSelection() }
            .map { caret -> EditorOffsetRange(caret.selectionStart, caret.selectionEnd) }
            .sortedBy { range -> min(range.firstOffset, range.secondOffset) }
            .toList()
        return EditorState(
            project = project,
            document = document,
            virtualFile = virtualFile,
            psiFile = psiFile,
            caretOffset = editor.caretModel.offset,
            selectedRanges = selections,
        )
    }

    private fun buildSnapshot(editorState: EditorState): AgentEditorContextSnapshot? {
        val document = editorState.document
        val snippet = extractSnippet(document, editorState.caretOffset, editorState.selectedRanges.firstOrNull()) ?: return null
        val psiFile = editorState.psiFile ?: PsiDocumentManager.getInstance(editorState.project).getPsiFile(document)
        val symbolName = resolveSymbolName(psiFile, document, editorState.caretOffset)
        val filePath = editorState.virtualFile?.path ?: psiFile?.virtualFile?.path
        val language = resolveLanguageName(psiFile)
        val selectedRanges = editorState.selectedRanges
            .map { range -> rangeForOffsets(document, range.firstOffset, range.secondOffset) }
            .sortedWith(compareBy<AgentPromptTextRange> { it.start.line }.thenBy { it.start.character })
        val selection = selectedRanges.firstOrNull() ?: caretRange(document, editorState.caretOffset)
        return AgentEditorContextSnapshot(
            filePath = filePath,
            virtualFile = editorState.virtualFile,
            language = language,
            snippet = snippet,
            symbolName = symbolName,
            selection = selection,
            selections = selectedRanges,
            activeSelectionContent = if (editorState.selectedRanges.size == 1) snippet.text else "",
        )
    }

    private fun resolveLanguageName(psiFile: PsiFile?): String? {
        return psiFile?.language?.id?.takeIf { it.isNotBlank() }
    }

    private fun resolveSymbolName(psiFile: PsiFile?, document: Document, rawOffset: Int): String? {
        val file = psiFile ?: return null
        val offset = adjustSymbolOffset(file, document, rawOffset)
        return resolveReferenceSymbolName(file, document, offset) ?: resolveEnclosingSymbolName(file, offset)
    }

    private fun adjustSymbolOffset(file: PsiFile, document: Document, rawOffset: Int): Int {
        if (file.textLength == 0 || document.textLength == 0) {
            return 0
        }
        val safeRawOffset = rawOffset.coerceIn(0, document.textLength)
        return TargetElementUtil.adjustOffset(file, document, safeRawOffset).coerceIn(0, file.textLength - 1)
    }

    private fun resolveReferenceSymbolName(file: PsiFile, document: Document, offset: Int): String? {
        val reference = file.findReferenceAt(offset) ?: return null
        val elementStartOffset = reference.element.textRange.startOffset
        val absoluteReferenceRange = ReferenceRange.getRanges(reference)
            .asSequence()
            .map { range -> range.shiftRight(elementStartOffset) }
            .firstOrNull { range -> range.containsOffset(offset) }
            ?: return null
        if (absoluteReferenceRange.startOffset < 0 || absoluteReferenceRange.endOffset > document.textLength) {
            return null
        }
        val referenceText = document.getText(absoluteReferenceRange)
        return extractReferenceSymbolName(referenceText, offset - absoluteReferenceRange.startOffset)
    }

    @VisibleForTesting
    fun extractReferenceSymbolName(referenceText: String, rawOffsetInReference: Int): String? {
        if (referenceText.isEmpty()) {
            return null
        }
        val offset = rawOffsetInReference.coerceIn(0, referenceText.length - 1)
        val start = referenceText.lastIndexOf('.', startIndex = offset).let { dotOffset ->
            if (dotOffset < 0) 0 else dotOffset + 1
        }
        val end = referenceText.indexOf('.', startIndex = offset + 1).let { dotOffset ->
            if (dotOffset < 0) referenceText.length else dotOffset
        }
        return normalizeSymbolName(referenceText.substring(start, end))
    }

    private fun resolveEnclosingSymbolName(file: PsiFile, offset: Int): String? {
        val maxOffset = max(0, file.textLength - 1)
        var element: PsiElement? = PsiUtilCore.getElementAtOffset(file, min(max(0, offset), maxOffset))
        while (element != null) {
            val namedElement = element as? PsiNamedElement
            val name = normalizeSymbolName(namedElement?.name)
            if (name != null) {
                return name
            }
            element = element.parent
        }
        return null
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

    private fun extractSnippet(document: Document, caretOffset: Int, selectionRange: EditorOffsetRange?): AgentPromptSnippet? {
        if (selectionRange != null) {
            val startOffset = min(selectionRange.firstOffset, selectionRange.secondOffset).coerceIn(0, document.textLength)
            val endOffsetExclusive = max(selectionRange.firstOffset, selectionRange.secondOffset).coerceIn(startOffset, document.textLength)
            val safeEndOffsetExclusive = max(startOffset + 1, endOffsetExclusive).coerceAtMost(document.textLength)
            val startLine = document.getLineNumber(startOffset) + 1
            val endLine = document.getLineNumber(max(startOffset, safeEndOffsetExclusive - 1)) + 1
            val truncateOutcome = truncate(document, startOffset, safeEndOffsetExclusive)
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
        val caretLine = document.getLineNumber(caretOffset.coerceIn(0, document.textLength))
        val startLineIndex = max(0, caretLine - CARET_CONTEXT_LINE_RADIUS)
        val endLineIndex = min(lineCount - 1, caretLine + CARET_CONTEXT_LINE_RADIUS)
        val startOffset = document.getLineStartOffset(startLineIndex)
        val endOffset = document.getLineEndOffset(endLineIndex)
        if (endOffset <= startOffset) {
            return null
        }
        val truncateOutcome = truncate(document, startOffset, endOffset)
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

    private fun truncate(document: Document, startOffset: Int, endOffsetExclusive: Int): TruncateOutcome {
        val safeStartOffset = startOffset.coerceIn(0, document.textLength)
        val safeEndOffset = endOffsetExclusive.coerceIn(safeStartOffset, document.textLength)
        val originalChars = safeEndOffset - safeStartOffset
        if (originalChars <= AGENT_PROMPT_MAX_SNIPPET_CHARS) {
            val text = document.getText(TextRange(safeStartOffset, safeEndOffset))
            return TruncateOutcome(
                text = text,
                originalChars = originalChars,
                includedChars = originalChars,
                truncated = false,
                reason = AgentPromptContextTruncationReason.NONE,
            )
        }
        val includedEndOffset = safeStartOffset + AGENT_PROMPT_MAX_SNIPPET_CHARS
        val prefix = document.getText(TextRange(safeStartOffset, includedEndOffset))
        val truncatedText = buildString(AGENT_PROMPT_MAX_SNIPPET_CHARS + 32) {
            append(prefix)
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

    private fun caretRange(document: Document, caretOffset: Int): AgentPromptTextRange {
        val position = positionForOffset(document, caretOffset)
        return AgentPromptTextRange(start = position, end = position)
    }

    private fun rangeForOffsets(document: Document, firstOffset: Int, secondOffset: Int): AgentPromptTextRange {
        val startOffset = min(firstOffset, secondOffset)
        val endOffset = max(firstOffset, secondOffset)
        return AgentPromptTextRange(
            start = positionForOffset(document, startOffset),
            end = positionForOffset(document, endOffset),
        )
    }

    private fun positionForOffset(document: Document, rawOffset: Int): AgentPromptTextPosition {
        val offset = rawOffset.coerceIn(0, document.textLength)
        val line = document.getLineNumber(offset)
        val character = offset - document.getLineStartOffset(line)
        return AgentPromptTextPosition(line = line, character = character)
    }
}

private data class EditorState(
    @JvmField val project: Project,
    @JvmField val document: Document,
    @JvmField val virtualFile: VirtualFile?,
    @JvmField val psiFile: PsiFile?,
    @JvmField val caretOffset: Int,
    @JvmField val selectedRanges: List<EditorOffsetRange>,
)

private data class EditorOffsetRange(
    @JvmField val firstOffset: Int,
    @JvmField val secondOffset: Int,
)

private data class TruncateOutcome(
    @JvmField val text: String,
    @JvmField val originalChars: Int,
    @JvmField val includedChars: Int,
    @JvmField val truncated: Boolean,
    @JvmField val reason: AgentPromptContextTruncationReason,
)

@ApiStatus.Internal
data class AgentEditorContextSnapshot(
    @JvmField val filePath: String?,
    @JvmField val virtualFile: VirtualFile?,
    @JvmField val language: String?,
    @JvmField val snippet: AgentPromptSnippet,
    @JvmField val symbolName: String?,
    @JvmField val selection: AgentPromptTextRange,
    @JvmField val selections: List<AgentPromptTextRange>,
    @JvmField val activeSelectionContent: String,
)

@ApiStatus.Internal
data class AgentPromptSnippet(
    @JvmField val text: String,
    @JvmField val startLine: Int,
    @JvmField val endLine: Int,
    @JvmField val fromSelection: Boolean,
    @JvmField val originalChars: Int,
    @JvmField val includedChars: Int,
    @JvmField val truncated: Boolean,
    @JvmField val truncationReason: AgentPromptContextTruncationReason,
)

@ApiStatus.Internal
data class AgentPromptTextRange(
    @JvmField val start: AgentPromptTextPosition,
    @JvmField val end: AgentPromptTextPosition,
)

@ApiStatus.Internal
data class AgentPromptTextPosition(
    @JvmField val line: Int,
    @JvmField val character: Int,
)
