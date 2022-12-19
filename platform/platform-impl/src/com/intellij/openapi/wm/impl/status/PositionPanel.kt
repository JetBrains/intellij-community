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
import com.intellij.ui.UIBundle
import com.intellij.util.Consumer
import com.intellij.util.cancelOnDispose
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.FocusUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
open class PositionPanel(project: Project) : EditorBasedWidget(project), Multiframe {
  @Volatile
  private var text: @NlsContexts.Label String? = null

  private val updateTextRequests = MutableSharedFlow<Editor?>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val countRequests = MutableSharedFlow<CodePointCountTask>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    @Suppress("DEPRECATION", "LeakingThis")
    project.coroutineScope.launch {
      withContext(Dispatchers.EDT) {
        FocusUtil.addFocusOwnerListener(this@PositionPanel) {
          updatePosition(getFocusedEditor())
        }
      }

      launch {
        updateTextRequests
          .debounce(100.milliseconds)
          .collectLatest { editor ->
            val empty = editor == null || DISABLE_FOR_EDITOR.isIn(editor)
            if (!empty && withContext(Dispatchers.EDT) { !isOurEditor(editor) }) {
              return@collectLatest
            }

            @Suppress("HardCodedStringLiteral")
            val newText = if (empty) "" else readAction { getPositionText(editor!!) }
            if (newText == text) {
              return@collectLatest
            }
            text = newText
            withContext(Dispatchers.EDT) {
              statusBar?.updateWidget(ID())
            }
          }
      }

      countRequests.collectLatest { task ->
        val count = task.calculate()
        withContext(Dispatchers.EDT) {
          text?.let {
            text = it.replace(CHAR_COUNT_UNKNOWN, count.toString())
            statusBar?.updateWidget(ID())
          }
        }
      }
    }.cancelOnDispose(this)
  }

  companion object {
    @JvmField
    val DISABLE_FOR_EDITOR = Key<Any>("positionPanel.disableForEditor")

    const val SPACE = "     "
    const val SEPARATOR = ":"
    private const val CHAR_COUNT_SYNC_LIMIT = 500000
    private const val CHAR_COUNT_UNKNOWN = "..."
  }

  override fun ID(): String = StatusBar.StandardWidgets.POSITION_PANEL

  override fun copy(): StatusBarWidget = PositionPanel(project)

  override fun getPresentation(): WidgetPresentation? {
    return object : TextPresentation {
      override fun getText(): String = this@PositionPanel.text ?: ""

      override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

      override fun getTooltipText(): String {
        val toolTip = UIBundle.message("go.to.line.command.name")
        val shortcut = getShortcutText()
        @Suppress("SpellCheckingInspection")
        return if (shortcut.isNotEmpty() && !Registry.`is`("ide.helptooltip.enabled")) "$toolTip ($shortcut)" else toolTip
      }

      override fun getShortcutText(): String = KeymapUtil.getFirstKeyboardShortcutText("GotoLine")

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

    val selectionModel = editor.selectionModel
    val caretCount = editor.caretModel.caretCount
    if (caretCount > 1) {
      return UIBundle.message("position.panel.caret.count", caretCount)
    }

    val message: @Nls StringBuilder = StringBuilder()
    val caret = editor.caretModel.logicalPosition
    message.append(caret.line + 1).append(SEPARATOR).append(caret.column + 1)
    if (selectionModel.hasSelection()) {
      val selectionStart = selectionModel.selectionStart
      val selectionEnd = selectionModel.selectionEnd
      if (selectionEnd > selectionStart) {
        message.append(" (")
        val countTask = CodePointCountTask(text = editor.document.immutableCharSequence,
                                           startOffset = selectionStart,
                                           endOffset = selectionEnd)
        if (countTask.isQuick) {
          val charCount = countTask.calculate()
          message.append(charCount).append(' ').append(UIBundle.message("position.panel.selected.chars.count", charCount))
        }
        else {
          message.append(CHAR_COUNT_UNKNOWN).append(' ').append(UIBundle.message("position.panel.selected.chars.count", 2))
          check(countRequests.tryEmit(countTask))
        }
        val selectionStartLine = editor.document.getLineNumber(selectionStart)
        val selectionEndLine = editor.document.getLineNumber(selectionEnd)
        if (selectionEndLine > selectionStartLine) {
          message.append(", ")
          message.append(UIBundle.message("position.panel.selected.line.breaks.count", selectionEndLine - selectionStartLine))
        }
        message.append(")")
      }
    }
    return message.toString()
  }

  private inner class CodePointCountTask(private val text: CharSequence,
                                         private val startOffset: Int,
                                         private val endOffset: Int) {
    val isQuick: Boolean
      get() = (endOffset - startOffset) < CHAR_COUNT_SYNC_LIMIT

    fun calculate(): Int = Character.codePointCount(text, startOffset, endOffset)
  }
}