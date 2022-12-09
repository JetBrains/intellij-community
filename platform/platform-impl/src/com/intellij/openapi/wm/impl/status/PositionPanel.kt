// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.ide.util.EditorGotoLineNumberDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.*
import com.intellij.openapi.wm.TextWidgetPresentation
import com.intellij.ui.UIBundle
import com.intellij.util.Consumer
import com.intellij.util.cancelOnDispose
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.FocusUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
open class PositionPanel(project: Project) : EditorBasedWidget(project), Multiframe {
  private val updateTextRequests = MutableSharedFlow<Editor?>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val charCountRequests = MutableSharedFlow<CodePointCountTask>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  companion object {
    @JvmField
    val DISABLE_FOR_EDITOR = Key<Any>("positionPanel.disableForEditor")

    const val SPACE = "     "
    const val SEPARATOR = ":"
    private const val CHAR_COUNT_SYNC_LIMIT = 500_000
    private const val CHAR_COUNT_UNKNOWN = "..."
  }

  override fun ID(): String = StatusBar.StandardWidgets.POSITION_PANEL

  override fun copy(): StatusBarWidget = PositionPanel(project)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getPresentation(): WidgetPresentation {
    return object : TextWidgetPresentation {
      override fun text(): Flow<@NlsContexts.Label String> {
        // combine uses the latest emitted values, so, we must emit something
        charCountRequests.tryEmit(CodePointCountTask(text = "", startOffset = 0, endOffset = 0))
        return updateTextRequests
          .debounce(100.milliseconds)
          .mapLatest { editor ->
            val empty = editor == null || DISABLE_FOR_EDITOR.isIn(editor)
            if (!empty && withContext(Dispatchers.EDT) { !isOurEditor(editor) }) {
              null
            }
            else {
              if (empty) "" else readAction { getPositionText(editor!!) }
            }
          }
          .filterNotNull()
          .combine(charCountRequests.mapLatest { task ->
            Character.codePointCount(task.text, task.startOffset, task.endOffset).toString()
          }) { text, charCount ->
            text.replaceFirst(CHAR_COUNT_UNKNOWN, charCount)
          }
      }

      //override fun getText(): String = this@PositionPanel.text ?: ""

      override val alignment: Float
        get() =  Component.CENTER_ALIGNMENT

      private val gotoShortcutText: String
        get() = KeymapUtil.getFirstKeyboardShortcutText("GotoLine")

      override fun getTooltipText(): String {
        val toolTip = UIBundle.message("go.to.line.command.name")
        val shortcut = gotoShortcutText
        @Suppress("SpellCheckingInspection")
        return if (shortcut.isNotEmpty() && !Registry.`is`("ide.helptooltip.enabled")) "$toolTip ($shortcut)" else toolTip
      }

      override fun getShortcutText() = gotoShortcutText

      override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer {
          val project = project
          val editor = getFocusedEditor() ?: return@Consumer
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
    }
  }

  override fun registerCustomListeners(connection: MessageBusConnection) {
    super.registerCustomListeners(connection)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        updatePosition(getEditor())
      }
    })
    val multicaster = EditorFactory.getInstance().eventMulticaster
    multicaster.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(e: CaretEvent) {
        val editor = e.editor
        // When multiple carets exist in editor, we don't show information about caret positions
        if (editor.caretModel.caretCount == 1 && isFocusedEditor(editor)) updatePosition(editor)
      }

      override fun caretAdded(e: CaretEvent) {
        updatePosition(e.editor)
      }

      override fun caretRemoved(e: CaretEvent) {
        updatePosition(e.editor)
      }
    }, this)
    multicaster.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        val editor = e.editor
        if (isFocusedEditor(editor)) updatePosition(editor)
      }
    }, this)
    multicaster.addDocumentListener(object : BulkAwareDocumentListener.Simple {
      override fun afterDocumentChange(document: Document) {
        EditorFactory.getInstance().editors(document)
          .filter { editor: Editor -> isFocusedEditor(editor) }
          .findFirst()
          .ifPresent { editor: Editor? -> updatePosition(editor) }
      }
    }, this)

    @Suppress("DEPRECATION")
    project.coroutineScope.launch(Dispatchers.EDT) {
      FocusUtil.addFocusOwnerListener(this@PositionPanel) {
        updatePosition(getFocusedEditor())
      }
    }.cancelOnDispose(this)
  }

  private fun isFocusedEditor(editor: Editor): Boolean {
    return getFocusedComponent() === editor.contentComponent
  }

  private fun updatePosition(editor: Editor?) {
    check(updateTextRequests.tryEmit(editor))
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