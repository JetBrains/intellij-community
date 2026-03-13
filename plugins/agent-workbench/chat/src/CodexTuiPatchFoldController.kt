// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import kotlin.time.Duration.Companion.milliseconds

internal const val CODEX_TUI_PATCH_FOLDING_REGISTRY_KEY: String = "agent.workbench.codex.tui.patch.folding"

internal fun shouldInstallCodexTuiPatchFolding(provider: AgentSessionProvider?): Boolean {
  return provider == AgentSessionProvider.CODEX && RegistryManager.getInstance().`is`(CODEX_TUI_PATCH_FOLDING_REGISTRY_KEY)
}

internal class CodexTuiPatchFoldController(
  private val terminalView: TerminalView,
  private val sessionState: kotlinx.coroutines.flow.StateFlow<TerminalViewSessionState>,
  parentScope: CoroutineScope,
) {
  private val foldState = CodexTuiPatchFoldState()
  private val rebuildJob: Job
  private val activeModelJob: Job
  private val terminationJob: Job
  private var mouseListenerEditor: Editor? = null

  private val mouseListener = object : EditorMouseListener {
    override fun mouseClicked(event: EditorMouseEvent) {
      handleMouseClick(event)
    }
  }

  init {
    rebuildJob = parentScope.launch {
      rebuildActiveEditorFolds()
      codexTuiPatchFoldRebuildFlow(terminalView)
        .conflate()
        .collect {
          rebuildActiveEditorFolds()
          delay(CODEX_TUI_PATCH_REBUILD_DEBOUNCE_MS.milliseconds)
        }
    }
    activeModelJob = parentScope.launch {
      terminalView.outputModels.active
        .drop(1)
        .collect {
          rebuildActiveEditorFolds()
        }
    }
    terminationJob = parentScope.launch {
      sessionState
        .filter { it == TerminalViewSessionState.Terminated }
        .collect {
          withContext(Dispatchers.EDT) {
            foldState.clear()
          }
        }
    }
  }

  fun dispose() {
    rebuildJob.cancel()
    activeModelJob.cancel()
    terminationJob.cancel()
    removeMouseListener()
    foldState.clear()
  }

  private fun handleMouseClick(event: EditorMouseEvent) {
    val foldRegion = event.collapsedFoldRegion
    if (foldRegion is CustomFoldRegion) {
      val hash = foldRegion.getUserData(CODEX_TUI_PATCH_FOLD_SIGNATURE_KEY) ?: return
      foldState.toggleBlock(event.editor, hash)
      event.consume()
      return
    }
    val inlay = event.inlay
    if (inlay != null && inlay.renderer is CodexTuiPatchExpandedHeaderRenderer) {
      val hash = inlay.getUserData(CODEX_TUI_PATCH_FOLD_SIGNATURE_KEY) as? String ?: return
      foldState.toggleBlock(event.editor, hash)
      event.consume()
      return
    }
  }

  private fun installMouseListenerIfNeeded(editor: Editor) {
    if (mouseListenerEditor === editor) return
    removeMouseListener()
    editor.addEditorMouseListener(mouseListener)
    mouseListenerEditor = editor
  }

  private fun removeMouseListener() {
    mouseListenerEditor?.removeEditorMouseListener(mouseListener)
    mouseListenerEditor = null
  }

  private suspend fun rebuildActiveEditorFolds() {
    val request = withContext(Dispatchers.EDT) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        foldState.clear()
        null
      }
      else {
        val editor = resolveTerminalEditor(terminalView)
        if (editor == null || editor.isDisposed) {
          foldState.clear()
          null
        }
        else {
          val model = terminalView.activeOutputModel()
          ActiveTerminalSnapshot(model = model, snapshot = model.takeSnapshot())
        }
      }
    } ?: return

    val matches = withContext(Dispatchers.Default) {
      detectCodexTuiPatchBlocks(request.snapshot)
    }

    withContext(Dispatchers.EDT) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        foldState.clear()
        return@withContext
      }

      val activeModel = terminalView.activeOutputModel()
      if (activeModel !== request.model || activeModel.modificationStamp != request.snapshot.modificationStamp) {
        return@withContext
      }

      val editor = resolveTerminalEditor(terminalView)
      if (editor == null || editor.isDisposed) {
        foldState.clear()
        return@withContext
      }

      installMouseListenerIfNeeded(editor)
      foldState.apply(editor, matches)
    }
  }

  private data class ActiveTerminalSnapshot(
    val model: TerminalOutputModel,
    val snapshot: TerminalOutputModelSnapshot,
  )
}

internal class CodexTuiPatchFoldState {
  private var currentEditor: Editor? = null
  private var currentMatches: List<CodexTuiPatchBlockMatch> = emptyList()
  private val expandedBlocks: MutableSet<String> = mutableSetOf()
  private val activeFoldRegions: MutableList<CustomFoldRegion> = mutableListOf()
  private val activeInlays: MutableList<Inlay<*>> = mutableListOf()

  fun apply(editor: Editor, matches: List<CodexTuiPatchBlockMatch>) {
    if (editor.isDisposed) {
      clear()
      return
    }

    if (currentEditor !== editor) {
      clear()
    }

    currentMatches = matches
    removeAllVisualElements(editor)

    val document = editor.document
    editor.foldingModel.runBatchFoldingOperation {
      for (match in matches) {
        if (match.contentHash in expandedBlocks) continue
        val startLine = document.getLineNumber(match.startOffset)
        val endLine = document.getLineNumber((match.endOffset - 1).coerceAtLeast(match.startOffset))
        val renderer = CodexTuiPatchSummaryRenderer(match.toSummaryData())
        val region = editor.foldingModel.addCustomLinesFolding(startLine, endLine, renderer)
        if (region != null) {
          region.putUserData(CODEX_TUI_PATCH_FOLD_SIGNATURE_KEY, match.contentHash)
          activeFoldRegions += region
        }
      }
    }

    for (match in matches) {
      if (match.contentHash !in expandedBlocks) continue
      val startLine = document.getLineNumber(match.startOffset)
      val offset = document.getLineStartOffset(startLine)
      val renderer = CodexTuiPatchExpandedHeaderRenderer(match.toSummaryData())
      val inlay = editor.inlayModel.addBlockElement(offset, true, true, 0, renderer)
      if (inlay != null) {
        inlay.putUserData(CODEX_TUI_PATCH_FOLD_SIGNATURE_KEY, match.contentHash)
        activeInlays += inlay
      }
    }

    currentEditor = if (activeFoldRegions.isEmpty() && activeInlays.isEmpty()) null else editor
  }

  fun toggleBlock(editor: Editor, contentHash: String) {
    if (contentHash in expandedBlocks) {
      expandedBlocks.remove(contentHash)
    }
    else {
      expandedBlocks.add(contentHash)
    }
    apply(editor, currentMatches)
  }

  fun clear() {
    val editor = currentEditor
    currentEditor = null
    currentMatches = emptyList()
    if (activeFoldRegions.isEmpty() && activeInlays.isEmpty()) {
      return
    }
    if (editor == null || editor.isDisposed) {
      activeFoldRegions.clear()
      activeInlays.clear()
      return
    }
    removeAllVisualElements(editor)
  }

  private fun removeAllVisualElements(editor: Editor) {
    if (activeFoldRegions.isNotEmpty()) {
      editor.foldingModel.runBatchFoldingOperation {
        activeFoldRegions.forEach { region ->
          if (region.isValid) {
            editor.foldingModel.removeFoldRegion(region)
          }
        }
        activeFoldRegions.clear()
      }
    }
    activeInlays.forEach { inlay ->
      if (inlay.isValid) {
        Disposer.dispose(inlay)
      }
    }
    activeInlays.clear()
  }
}

internal data class CodexTuiPatchBlockMatch(
  val verb: String,
  val target: String,
  val headerText: String,
  val startOffset: Int,
  val endOffset: Int,
  val lineCount: Int,
  val added: Int,
  val removed: Int,
  val fileCount: Int,
  val contentHash: String,
)

private fun CodexTuiPatchBlockMatch.toSummaryData(): CodexTuiPatchSummaryData = CodexTuiPatchSummaryData(
  verb = verb,
  target = target,
  added = added,
  removed = removed,
)

private data class ParsedCodexTuiPatchBlock(
  val match: CodexTuiPatchBlockMatch,
  val nextLineIndex: TerminalLineIndex,
)

internal fun detectCodexTuiPatchBlocks(snapshot: TerminalOutputModelSnapshot): List<CodexTuiPatchBlockMatch> {
  if (snapshot.lineCount == 0) {
    return emptyList()
  }

  val firstScanLine = if (snapshot.lineCount > CODEX_TUI_PATCH_SCAN_LIMIT_LINES) {
    snapshot.lastLineIndex - (CODEX_TUI_PATCH_SCAN_LIMIT_LINES - 1).toLong()
  }
  else {
    snapshot.firstLineIndex
  }

  val matches = mutableListOf<CodexTuiPatchBlockMatch>()
  var line = firstScanLine
  while (line <= snapshot.lastLineIndex) {
    val lineText = snapshot.lineText(line)
    if (!CODEX_TUI_PATCH_HEADER_REGEX.matches(lineText)) {
      line += 1L
      continue
    }

    val parsedBlock = parseCodexTuiPatchBlock(snapshot, line, lineText)
    if (parsedBlock == null) {
      line += 1L
      continue
    }

    val block = parsedBlock.match
    if (block.fileCount > 1 || block.added + block.removed >= CODEX_TUI_PATCH_MIN_CHANGED_LINES || block.lineCount >= CODEX_TUI_PATCH_MIN_LINE_COUNT) {
      matches += block
    }
    line = parsedBlock.nextLineIndex
  }
  return matches
}

private fun parseCodexTuiPatchBlock(
  snapshot: TerminalOutputModelSnapshot,
  headerLineIndex: TerminalLineIndex,
  headerText: String,
): ParsedCodexTuiPatchBlock? {
  val headerMatch = CODEX_TUI_PATCH_HEADER_REGEX.matchEntire(headerText) ?: return null
  val verb = headerMatch.groupValues[1]
  val added = headerMatch.groupValues[3].toInt()
  val removed = headerMatch.groupValues[4].toInt()
  val target = headerMatch.groupValues[2]
  val fileCount = CODEX_TUI_PATCH_FILE_COUNT_REGEX.matchEntire(target)?.groupValues?.get(1)?.toInt() ?: 1

  var lineCount = 1
  var hasBodyContent = false
  var lastIncludedLine = headerLineIndex
  val rawBlockText = StringBuilder(headerText)
  var line = headerLineIndex + 1L
  while (line <= snapshot.lastLineIndex) {
    val text = snapshot.lineText(line)
    if (text.isNotEmpty() && !isCodexTuiPatchContinuationLine(text)) {
      break
    }
    rawBlockText.append('\n').append(text)
    lineCount++
    lastIncludedLine = line
    hasBodyContent = hasBodyContent || isCodexTuiPatchBodyLine(text)
    line += 1L
  }

  if (!hasBodyContent) {
    return null
  }

  val startOffset = (snapshot.getStartOfLine(headerLineIndex) - snapshot.startOffset).toInt()
  val endOffset = (snapshot.getEndOfLine(lastIncludedLine, includeEOL = true) - snapshot.startOffset).toInt()
  if (endOffset <= startOffset) {
    return null
  }

  return ParsedCodexTuiPatchBlock(
    match = CodexTuiPatchBlockMatch(
      verb = verb,
      target = target,
      headerText = headerText,
      startOffset = startOffset,
      endOffset = endOffset,
      lineCount = lineCount,
      added = added,
      removed = removed,
      fileCount = fileCount,
      contentHash = rawBlockText.toString().hashCode().toString(16),
    ),
    nextLineIndex = line,
  )
}

private fun codexTuiPatchFoldRebuildFlow(terminalView: TerminalView): Flow<Unit> = callbackFlow {
  val scope = this
  withContext(Dispatchers.EDT) {
    val listenerDisposable = scope.asDisposable()
    val listener = object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!scope.isActive || event.isTypeAhead) {
          return
        }
        scope.trySend(Unit)
      }
    }
    terminalView.outputModels.regular.addListener(listenerDisposable, listener)
    terminalView.outputModels.alternative.addListener(listenerDisposable, listener)
  }
  awaitClose()
}

private fun resolveTerminalEditor(terminalView: TerminalView): Editor? {
  val dataContext = DataManager.getInstance().getDataContext(terminalView.component)
  return TerminalActionUtil.EDITOR_KEY.getData(dataContext)
}

private fun TerminalOutputModelSnapshot.lineText(line: TerminalLineIndex): String {
  return getText(getStartOfLine(line), getEndOfLine(line)).toString()
}

private fun isCodexTuiPatchContinuationLine(text: String): Boolean {
  return text.isEmpty() || CODEX_TUI_PATCH_FILE_LINE_REGEX.matches(text) || CODEX_TUI_PATCH_INDENTED_LINE_REGEX.matches(text)
}

private fun isCodexTuiPatchBodyLine(text: String): Boolean {
  return CODEX_TUI_PATCH_FILE_LINE_REGEX.matches(text) || CODEX_TUI_PATCH_DIFF_LINE_REGEX.matches(text)
}

private val CODEX_TUI_PATCH_HEADER_REGEX = Regex("^• (Added|Edited|Deleted) (.+) \\(\\+([0-9]+) -([0-9]+)\\)$")
private val CODEX_TUI_PATCH_FILE_COUNT_REGEX = Regex("^([0-9]+) files?$")
private val CODEX_TUI_PATCH_FILE_LINE_REGEX = Regex("^\\u0020{2}└ .+")
private val CODEX_TUI_PATCH_DIFF_LINE_REGEX = Regex("^\\u0020{4}[0-9]+ [+-].*")
private val CODEX_TUI_PATCH_INDENTED_LINE_REGEX = Regex("^\\u0020{4}.*")

private val CODEX_TUI_PATCH_FOLD_SIGNATURE_KEY: Key<String> = Key.create("agent.workbench.codex.tui.patch.fold.signature")

private const val CODEX_TUI_PATCH_SCAN_LIMIT_LINES: Int = 400
private const val CODEX_TUI_PATCH_REBUILD_DEBOUNCE_MS: Long = 350
private const val CODEX_TUI_PATCH_MIN_CHANGED_LINES: Int = 8
private const val CODEX_TUI_PATCH_MIN_LINE_COUNT: Int = 8
