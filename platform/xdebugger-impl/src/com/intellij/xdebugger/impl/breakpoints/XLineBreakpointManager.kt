// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.DocumentUtil
import com.intellij.util.SlowOperations
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction
import com.intellij.xdebugger.impl.breakpoints.InlineBreakpointInlayManager.Companion.getInstance
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.MouseEvent

@Internal
class XLineBreakpointManager(private val project: Project, coroutineScope: CoroutineScope, private val isEnabled: Boolean) {
  private val cs = coroutineScope.childScope("XLineBreakpointManager")

  private val myBreakpoints = MultiMap.createConcurrent<String, XLineBreakpointProxy>()
  private val breakpointUpdateQueue: MergingUpdateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "XLine breakpoints",
    mergingTimeSpan = 300,
    coroutineScope = cs,
  )

  private var myDragDetected = false

  init {
    val busConnection = project.messageBus.connect(cs)

    if (!project.isDefault) {
      val editorEventMulticaster = EditorFactory.getInstance().eventMulticaster
      editorEventMulticaster.addDocumentListener(MyDocumentListener(), cs.asDisposable())
      editorEventMulticaster.addEditorMouseListener(MyEditorMouseListener(), cs.asDisposable())
      editorEventMulticaster.addEditorMouseMotionListener(MyEditorMouseMotionListener(), cs.asDisposable())

      busConnection.subscribe(XDependentBreakpointListener.TOPIC, MyDependentBreakpointListener())
      busConnection.subscribe(VirtualFileManager.VFS_CHANGES, BulkVirtualFileListenerAdapter(object : VirtualFileUrlChangeAdapter() {
        override fun fileUrlChanged(oldUrl: String, newUrl: String) {
          myBreakpoints.values().forEach { breakpoint ->
            val url = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
            if (FileUtil.startsWith(url, oldUrl)) {
              breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length))
            }
          }
        }

        override fun fileDeleted(event: VirtualFileEvent) {
          removeBreakpoints(myBreakpoints[event.file.url])
        }
      }))

      Registry.get(XDebuggerUtil.INLINE_BREAKPOINTS_KEY).addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          for (fileUrl in myBreakpoints.keySet()) {
            val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: continue
            if (XDebuggerUtil.areInlineBreakpointsEnabled(file)) continue
            val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
            // Multiple breakpoints on the single line should be joined in this case.
            updateBreakpoints(document)
          }
        }
      }, cs.asDisposable())
    }

    // Update breakpoints colors if global color schema was changed
    busConnection.subscribe(EditorColorsManager.TOPIC, MyEditorColorsListener())
    busConnection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileContentLoaded(file: VirtualFile, document: Document) {
        myBreakpoints[file.url].asSequence()
          .filter { it.getHighlighter() == null }
          .forEach { queueBreakpointUpdate(it) }
      }
    })

    if (!isEnabled) {
      cs.cancel()
    }
  }

  fun updateBreakpointsUI() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    StartupManager.getInstance(project).runAfterOpened { queueAllBreakpointsUpdate() }
  }

  @Deprecated("Use {@link #registerBreakpoint(XLineBreakpointProxy, boolean)} instead")
  fun registerBreakpoint(breakpoint: XLineBreakpointImpl<*>, initUI: Boolean) {
    registerBreakpoint(breakpoint.asProxy(), initUI)
  }

  fun registerBreakpoint(breakpoint: XLineBreakpointProxy, initUI: Boolean) {
    if (initUI) {
      updateBreakpointNow(breakpoint)
    }
    myBreakpoints.putValue(breakpoint.getFile()?.url ?: breakpoint.getFileUrl(), breakpoint)
  }

  fun unregisterBreakpoint(breakpoint: XLineBreakpointProxy) {
    myBreakpoints.remove(breakpoint.getFile()?.url ?: breakpoint.getFileUrl(), breakpoint)
  }

  fun getDocumentBreakpointProxies(document: Document): Collection<XLineBreakpointProxy> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    return myBreakpoints[file.url]
  }

  fun getDocumentBreakpoints(document: Document): Collection<XLineBreakpointImpl<*>> {
    return getDocumentBreakpointProxies(document).filterIsInstance<XLineBreakpointProxy.Monolith>().map { it.breakpoint }
  }

  @RequiresEdt
  private fun updateBreakpoints(document: Document) {
    if (!isEnabled) {
      return
    }
    val breakpoints = getDocumentBreakpointProxies(document)

    if (breakpoints.isEmpty() || ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    SlowOperations.knownIssue("IJPL-162343").use {
      breakpoints.forEach { it.updatePosition() }
    }

    // Check if two or more breakpoints occurred at the same position and remove duplicates.
    val (valid, invalid) = breakpoints.partition {
      val highlighter = it.getHighlighter()
      highlighter != null && highlighter.isValid
    }
    removeBreakpoints(invalid)
    val areInlineBreakpoints = XDebuggerUtil.areInlineBreakpointsEnabled(FileDocumentManager.getInstance().getFile(document))
    val duplicates = valid
      .groupBy { b ->
        if (areInlineBreakpoints) {
          // We cannot show multiple breakpoints of the same type at the same position.
          // Note that highlightRange might be null, so we still have to add line as an identity element.
          SlowOperations.knownIssue("IJPL-162343").use {
            Triple(b.type, b.getLine(), b.getHighlightRange()?.startOffset)
          }
        } else {
          // We cannot show multiple breakpoints of any type at the same line.
          b.getLine()
        }
      }
      .values
      .filter { it.size > 1 }
      .flatMap { it.drop(1) }
    removeBreakpoints(duplicates)
  }

  private fun removeBreakpoints(toRemove: Collection<XBreakpointProxy>?) {
    val manager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    for (breakpoint in toRemove.orEmpty()) {
      manager.removeBreakpoint(breakpoint)
    }
  }

  fun breakpointChanged(breakpoint: XLineBreakpointProxy) {
    if (EDT.isCurrentThreadEdt()) {
      updateBreakpointNow(breakpoint)
    }
    else {
      queueBreakpointUpdate(breakpoint, null)
    }
  }

  @JvmOverloads
  fun queueBreakpointUpdate(slave: XBreakpoint<*>?, callOnUpdate: Runnable? = null) {
    if (slave is XLineBreakpointImpl<*>) {
      queueBreakpointUpdate(slave.asProxy(), callOnUpdate)
    }
  }

  fun queueBreakpointUpdateCallback(breakpoint: XLineBreakpointImpl<*>?, callback: Runnable) {
    breakpointUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        callback.run()
      }
    })
  }

  fun queueBreakpointUpdateCallback(breakpoint: XLineBreakpointProxy, callback: Runnable) {
    breakpointUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        callback.run()
      }
    })
  }

  // Skip waiting 300ms in myBreakpointsUpdateQueue (good for sync updates like enable/disable or create new breakpoint)
  private fun updateBreakpointNow(breakpoint: XLineBreakpointProxy) {
    queueBreakpointUpdate(breakpoint)
    breakpointUpdateQueue.sendFlush()
  }

  private fun queueBreakpointUpdate(breakpoint: XLineBreakpointProxy, callOnUpdate: Runnable? = null) {
    breakpointUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        breakpoint.doUpdateUI {
          callOnUpdate?.run()
        }
      }
    })
  }

  fun queueAllBreakpointsUpdate() {
    breakpointUpdateQueue.queue(object : Update("all breakpoints") {
      override fun run() {
        myBreakpoints.values().forEach { it.doUpdateUI() }
      }
    })
    // skip waiting
    breakpointUpdateQueue.sendFlush()
  }

  private inner class MyDocumentListener : DocumentListener {
    override fun documentChanged(e: DocumentEvent) {
      val document = e.document
      val breakpoints = getDocumentBreakpointProxies(document)
      if (!breakpoints.isEmpty()) {
        breakpointUpdateQueue.queue(object : Update(document) {
          override fun run() {
            ApplicationManager.getApplication().invokeLater {
              updateBreakpoints(document)
            }
          }
        })

        getInstance(project).redrawDocument(e)
      }
    }
  }

  private inner class MyEditorMouseMotionListener : EditorMouseMotionListener {
    override fun mouseDragged(e: EditorMouseEvent) {
      myDragDetected = true
    }
  }

  private inner class MyEditorMouseListener : EditorMouseListener {
    override fun mousePressed(e: EditorMouseEvent) {
      myDragDetected = false
    }

    override fun mouseClicked(e: EditorMouseEvent) {
      val editor = e.editor
      val mouseEvent = e.mouseEvent
      if ((mouseEvent.isPopupTrigger || mouseEvent.isMetaDown || mouseEvent.isControlDown)
          || mouseEvent.button != MouseEvent.BUTTON1
          || DiffUtil.isDiffEditor(editor)
          || !isInsideClickableGutterArea(e)
          || ConsoleViewUtil.isConsoleViewEditor(editor)
          || !isFromMyProject(editor)
          || (editor.selectionModel.hasSelection() && myDragDetected)
      ) {
        return
      }

      val document = editor.document
      PsiDocumentManager.getInstance(project).commitDocument(document)
      val line = EditorUtil.yToLogicalLineNoCustomRenderers(editor, mouseEvent.y)
      val file = FileDocumentManager.getInstance().getFile(document)
      if (DocumentUtil.isValidLine(line, document) && file != null) {
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)
        if (action == null) throw AssertionError("'" + IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT + "' action not found")
        val dataContext = SimpleDataContext.getSimpleContext(BREAKPOINT_LINE_KEY, line,
                                                             DataManager.getInstance().getDataContext(mouseEvent.component))
        val event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.EDITOR_GUTTER, dataContext)
        // Call handler directly so that it will be called on frontend
        val handler = ToggleLineBreakpointAction.ourHandler
        if (handler.isEnabled(project, event)) {
          handler.perform(project, event)
          // statistics reporting
          ActionsCollectorImpl.onAfterActionInvoked(action, event, AnActionResult.PERFORMED)
        }
      }
    }

    private fun isInsideClickableGutterArea(e: EditorMouseEvent): Boolean {
      if (isNewUI() && e.area == EditorMouseEventArea.LINE_NUMBERS_AREA) {
        return getInstance().showBreakpointsOverLineNumbers
      }
      if (isNewUI() && e.editor.settings.isLineNumbersAfterIcons && e.editor.settings.isLineNumbersShown) {
        return false
      }
      if (e.area != EditorMouseEventArea.LINE_MARKERS_AREA && e.area != EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        return false
      }
      return e.mouseEvent.x <= (e.editor as EditorEx).gutterComponentEx.whitespaceSeparatorOffset
    }
  }

  private fun isFromMyProject(editor: Editor): Boolean {
    if (project === editor.project) {
      return true
    }

    for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
      if (fileEditor is TextEditor && fileEditor.editor == editor) {
        return true
      }
    }
    return false
  }

  private inner class MyDependentBreakpointListener : XDependentBreakpointListener {
    override fun dependencySet(slave: XBreakpoint<*>, master: XBreakpoint<*>) {
      queueBreakpointUpdate(slave)
    }

    override fun dependencyCleared(breakpoint: XBreakpoint<*>?) {
      queueBreakpointUpdate(breakpoint)
    }
  }

  private inner class MyEditorColorsListener : EditorColorsListener {
    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
      updateBreakpointsUI()
    }
  }

  companion object {
    @JvmField
    val BREAKPOINT_LINE_KEY: DataKey<Int> = DataKey.create("xdebugger.breakpoint.line")
  }
}
