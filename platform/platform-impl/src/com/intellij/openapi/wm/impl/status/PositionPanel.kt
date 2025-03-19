// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.EditorGotoLineNumberDialog
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.TextWidgetPresentation
import com.intellij.openapi.wm.WidgetPresentationDataContext
import com.intellij.ui.UIBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.event.MouseEvent
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.milliseconds

@Internal
@OptIn(FlowPreview::class)
open class PositionPanel(private val dataContext: WidgetPresentationDataContext,
                         scope: CoroutineScope,
                         protected val helper: EditorBasedWidgetHelper = EditorBasedWidgetHelper(dataContext.project)) : TextWidgetPresentation {
  private val updateTextRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    .also { it.tryEmit(Unit) }
  private val charCountRequests = MutableSharedFlow<CodePointCountTask>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  companion object {
    @JvmField
    val DISABLE_FOR_EDITOR: Key<Any> = Key<Any>("positionPanel.disableForEditor")

    const val SPACE: String = "     "
    const val SEPARATOR: String = ":"
    private const val CHAR_COUNT_SYNC_LIMIT = 500_000
    private const val CHAR_COUNT_UNKNOWN = "..."
  }

  init {
    val disposable = Disposer.newDisposable()
    val multicaster = EditorFactory.getInstance().eventMulticaster
    multicaster.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(e: CaretEvent) {
        val editor = e.editor
        // when multiple carets exist in editor, we don't show information about caret positions
        if (editor.caretModel.caretCount == 1) {
          updatePosition(editor)
        }
      }

      override fun caretAdded(e: CaretEvent) {
        updatePosition(e.editor)
      }

      override fun caretRemoved(e: CaretEvent) {
        updatePosition(e.editor)
      }
    }, disposable)
    multicaster.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        // react to "select all" action
        updatePosition(e.editor)
      }
    }, disposable)
    multicaster.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun afterDocumentChange(document: Document) {
        EditorFactory.getInstance().editors(document).asSequence()
          .forEach(::updatePosition)
      }
    }, disposable)
    scope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }

    // combine uses the latest emitted values, so, we must emit something
    check(charCountRequests.tryEmit(CodePointCountTask(text = "", startOffset = 0, endOffset = 0)))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun text(): Flow<@NlsContexts.Label String?> {
    return combine(updateTextRequests, dataContext.currentFileEditor) { _, fileEditor -> (fileEditor as? TextEditor)?.editor }
      .debounce(100.milliseconds)
      .mapLatest { editor ->
        if (editor == null || DISABLE_FOR_EDITOR.isIn(editor)) null else readAction { getPositionText(editor) }
      }
      .combine(charCountRequests.mapLatest { task ->
        Character.codePointCount(task.text, task.startOffset, task.endOffset).toString()
      }) { text, charCount ->
        text?.replaceFirst(CHAR_COUNT_UNKNOWN, charCount)
      }
  }

  override val alignment: Float
    get() = Component.CENTER_ALIGNMENT

  private val gotoShortcutText: String
    get() = KeymapUtil.getFirstKeyboardShortcutText("GotoLine")

  override suspend fun getTooltipText(): String {
    val toolTip = UIBundle.message("go.to.line.command.name")
    val shortcut = gotoShortcutText
    @Suppress("SpellCheckingInspection")
    return if (shortcut.isNotEmpty() && !UISettings.isIdeHelpTooltipEnabled()) "$toolTip ($shortcut)" else toolTip
  }

  override suspend fun getShortcutText(): String = gotoShortcutText

  override fun getClickConsumer(): (MouseEvent) -> Unit {
    return h@{
      val project = helper.project
      val editor = (dataContext.currentFileEditor.value as? TextEditor)?.editor ?: return@h
      CommandProcessor.getInstance().executeCommand(
        project,
        {
          val dialog = EditorGotoLineNumberDialog(project, editor)
          dialog.show()
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
        },
        UIBundle.message("go.to.line.command.name"),
        null
      )
    }
  }

  private fun updatePosition(editor: Editor) {
    val ourEditor = (dataContext.currentFileEditor.value as? TextEditor)?.editor
    if (editor === ourEditor) {
      check(updateTextRequests.tryEmit(Unit))
    }
  }

  @Suppress("HardCodedStringLiteral")
  private fun getPositionText(editor: Editor): @NlsContexts.Label String {
    if (editor.isDisposed) {
      return ""
    }

    val caretModel = editor.caretModel
    val caretCount = caretModel.caretCount
    if (caretCount > 1) {
      return UIBundle.message("position.panel.caret.count", caretCount)
    }

    val message: @Nls StringBuilder = StringBuilder()
    val caret = caretModel.currentCaret
    val logicalPosition = caret.logicalPosition
    message.append(logicalPosition.line + 1).append(SEPARATOR).append(logicalPosition.column + 1)
    if (!caret.hasSelection()) {
      return message.toString()
    }

    val selectionStart = caret.selectionStart
    val selectionEnd = caret.selectionEnd
    if (selectionEnd <= selectionStart) {
      return message.toString()
    }

    message.append(" (")
    if ((selectionEnd - selectionStart) < CHAR_COUNT_SYNC_LIMIT) {
      val charCount = Character.codePointCount(editor.document.immutableCharSequence, selectionStart, selectionEnd)
      message.append(charCount).append(' ').append(UIBundle.message("position.panel.selected.chars.count", charCount))
    }
    else {
      message.append(CHAR_COUNT_UNKNOWN).append(' ').append(UIBundle.message("position.panel.selected.chars.count", 2))
      check(charCountRequests.tryEmit(CodePointCountTask(text = editor.document.immutableCharSequence,
                                                         startOffset = selectionStart,
                                                         endOffset = selectionEnd)))
    }

    val selectionStartLine = editor.document.getLineNumber(selectionStart)
    val selectionEndLine = editor.document.getLineNumber(selectionEnd)
    if (selectionEndLine > selectionStartLine) {
      message.append(", ")
      message.append(UIBundle.message("position.panel.selected.line.breaks.count", selectionEndLine - selectionStartLine))
    }
    message.append(')')
    return message.toString()
  }
}

private class CodePointCountTask(@JvmField val text: CharSequence, @JvmField val startOffset: Int, @JvmField val endOffset: Int)