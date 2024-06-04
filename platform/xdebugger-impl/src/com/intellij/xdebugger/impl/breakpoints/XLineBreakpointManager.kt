// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
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
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.Alarm
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.breakpoints.InlineBreakpointInlayManager.Companion.getInstance
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.awt.event.MouseEvent
import java.util.function.Consumer

class XLineBreakpointManager(private val myProject: Project) {
  private val myBreakpoints = MultiMap.createConcurrent<String, XLineBreakpointImpl<*>>()
  private val myBreakpointsUpdateQueue: MergingUpdateQueue

  fun updateBreakpointsUI() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    StartupManager.getInstance(myProject).runAfterOpened { queueAllBreakpointsUpdate() }
  }

  fun registerBreakpoint(breakpoint: XLineBreakpointImpl<*>, initUI: Boolean) {
    if (initUI) {
      updateBreakpointNow(breakpoint)
    }
    myBreakpoints.putValue(breakpoint.fileUrl, breakpoint)
  }

  fun unregisterBreakpoint(breakpoint: XLineBreakpointImpl<*>) {
    myBreakpoints.remove(breakpoint.fileUrl, breakpoint)
  }

  fun getDocumentBreakpoints(document: Document): Collection<XLineBreakpointImpl<*>> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    return myBreakpoints[file.url]
  }

  @RequiresEdt
  private fun updateBreakpoints(document: Document) {
    val breakpoints = getDocumentBreakpoints(document)

    if (breakpoints.isEmpty() || ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val areInlineBreakpoints = XDebuggerUtil.areInlineBreakpointsEnabled(FileDocumentManager.getInstance().getFile(document))

    val positions = IntOpenHashSet()
    val toRemove = mutableListOf<XLineBreakpoint<*>>()
    for (breakpoint in breakpoints) {
      breakpoint.updatePosition()
      val position = if (areInlineBreakpoints) breakpoint.offset else breakpoint.line
      if (!breakpoint.isValid || !positions.add(position)) {
        toRemove.add(breakpoint)
      }
    }

    removeBreakpoints(toRemove)
  }

  private fun removeBreakpoints(toRemove: Collection<XLineBreakpoint<*>>?) {
    if (!toRemove.isNullOrEmpty()) {
      (XDebuggerManager.getInstance(myProject).breakpointManager as XBreakpointManagerImpl).removeBreakpoints(toRemove)
    }
  }

  fun breakpointChanged(breakpoint: XLineBreakpointImpl<*>) {
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
      queueBreakpointUpdate(slave, callOnUpdate)
    }
  }

  // Skip waiting 300ms in myBreakpointsUpdateQueue (good for sync updates like enable/disable or create new breakpoint)
  private fun updateBreakpointNow(breakpoint: XLineBreakpointImpl<*>) {
    queueBreakpointUpdate(breakpoint)
    myBreakpointsUpdateQueue.sendFlush()
  }

  private fun queueBreakpointUpdate(breakpoint: XLineBreakpointImpl<*>, callOnUpdate: Runnable? = null) {
    myBreakpointsUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        breakpoint.doUpdateUI(callOnUpdate ?: EmptyRunnable.INSTANCE)
      }
    })
  }

  fun queueAllBreakpointsUpdate() {
    myBreakpointsUpdateQueue.queue(object : Update("all breakpoints") {
      override fun run() {
        myBreakpoints.values().forEach { it.doUpdateUI(EmptyRunnable.INSTANCE) }
      }
    })
    // skip waiting
    myBreakpointsUpdateQueue.sendFlush()
  }

  private inner class MyDocumentListener : DocumentListener {
    override fun documentChanged(e: DocumentEvent) {
      val document = e.document
      val breakpoints = getDocumentBreakpoints(document)
      if (!breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(object : Update(document) {
          override fun run() {
            ApplicationManager.getApplication().invokeLater {
              updateBreakpoints(document)
            }
          }
        })

        getInstance(myProject).redrawDocument(e)
      }
    }
  }

  private var myDragDetected = false

  init {
    val busConnection = myProject.messageBus.connect()

    if (!myProject.isDefault) {
      val editorEventMulticaster = EditorFactory.getInstance().eventMulticaster
      editorEventMulticaster.addDocumentListener(MyDocumentListener(), myProject)
      editorEventMulticaster.addEditorMouseListener(MyEditorMouseListener(), myProject)
      editorEventMulticaster.addEditorMouseMotionListener(MyEditorMouseMotionListener(), myProject)

      busConnection.subscribe(XDependentBreakpointListener.TOPIC, MyDependentBreakpointListener())
      busConnection.subscribe(VirtualFileManager.VFS_CHANGES, BulkVirtualFileListenerAdapter(object : VirtualFileUrlChangeAdapter() {
        override fun fileUrlChanged(oldUrl: String, newUrl: String) {
          myBreakpoints.values().forEach { breakpoint ->
            val url = breakpoint.fileUrl
            if (FileUtil.startsWith(url, oldUrl)) {
              breakpoint.fileUrl = newUrl + url.substring(oldUrl.length)
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
      }, myProject)
    }
    myBreakpointsUpdateQueue = MergingUpdateQueue("XLine breakpoints", 300, true, null, myProject, null, Alarm.ThreadToUse.POOLED_THREAD)

    // Update breakpoints colors if global color schema was changed
    busConnection.subscribe(EditorColorsManager.TOPIC, MyEditorColorsListener())
    busConnection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileContentLoaded(file: VirtualFile, document: Document) {
        myBreakpoints[file.url].asSequence()
          .filter { it.highlighter == null }
          .forEach { queueBreakpointUpdate(it) }
      }
    })
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
      PsiDocumentManager.getInstance(myProject).commitDocument(document)
      val line = EditorUtil.yToLogicalLineNoCustomRenderers(editor, mouseEvent.y)
      val file = FileDocumentManager.getInstance().getFile(document)
      if (DocumentUtil.isValidLine(line, document) && file != null) {
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)
        if (action == null) throw AssertionError("'" + IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT + "' action not found")
        val dataContext = SimpleDataContext.getSimpleContext(BREAKPOINT_LINE_KEY, line,
                                                             DataManager.getInstance().getDataContext(mouseEvent.component))
        val event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.EDITOR_GUTTER, dataContext)
        performActionDumbAwareWithCallbacks(action, event)
      }
    }

    private fun isInsideClickableGutterArea(e: EditorMouseEvent): Boolean {
      if (isNewUI() && e.area == EditorMouseEventArea.LINE_NUMBERS_AREA) {
        return getInstance().showBreakpointsOverLineNumbers
      }
      if (e.area != EditorMouseEventArea.LINE_MARKERS_AREA && e.area != EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        return false
      }
      return e.mouseEvent.x <= (e.editor as EditorEx).gutterComponentEx.whitespaceSeparatorOffset
    }
  }

  private fun isFromMyProject(editor: Editor): Boolean {
    if (myProject === editor.project) {
      return true
    }

    for (fileEditor in FileEditorManager.getInstance(myProject).allEditors) {
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
