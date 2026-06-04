// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.BreakpointArea
import com.intellij.openapi.editor.impl.InterLineBreakpointProperties
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLightLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
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
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.event.MouseEvent

private val log = logger<XLineBreakpointManager>()

@ApiStatus.Internal
class XLineBreakpointManager(
  private val project: Project,
  coroutineScope: CoroutineScope,
  isEnabled: Boolean,
  private val manager: XBreakpointManagerProxy,
): XLineBreakpointManagerProxy {
  private val cs = coroutineScope.childScope("XLineBreakpointManager")

  private val myBreakpoints = MultiMap.createConcurrent<String, XLineBreakpointProxy>()
  private val breakpointUpdateQueue: MergingUpdateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "XLine breakpoints",
    mergingTimeSpan = 300,
    coroutineScope = cs,
  )

  private var myDragDetected = false
  private var immediateUiUpdateOnEdtAllowed = true

  init {
    val disposable = cs.asDisposable()
    val busConnection = project.messageBus.connect(disposable)

    if (!project.isDefault) {
      val editorEventMulticaster = EditorFactory.getInstance().eventMulticaster
      editorEventMulticaster.addDocumentListener(MyDocumentListener(), disposable)
      editorEventMulticaster.addEditorMouseListener(MyEditorMouseListener(), disposable)
      editorEventMulticaster.addEditorMouseMotionListener(MyEditorMouseMotionListener(), disposable)

      busConnection.subscribe(XDependentBreakpointListener.TOPIC, MyDependentBreakpointListener())

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
      }, disposable)
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

  @ApiStatus.Internal
  fun onFileDeleted(url: String) {
    removeBreakpoints(myBreakpoints[url])
  }

  @ApiStatus.Internal
  fun onFileUrlChanged(oldUrl: String, newUrl: String) {
    myBreakpoints.values().forEach { breakpoint ->
      val url = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
      if (FileUtil.startsWith(url, oldUrl)) {
        breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length))
      }
    }
  }

  fun updateBreakpointsUI() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    StartupManager.getInstance(project).runAfterOpened { queueAllBreakpointsUpdate() }
  }

  @Deprecated("Use {@link #registerBreakpoint(XLineBreakpointProxy, boolean)} instead")
  fun registerBreakpoint(breakpoint: XLineBreakpoint<*>, initUI: Boolean) {
    val proxy = XDebuggerEntityConverter.asProxy(breakpoint) as? XLineBreakpointProxy
    if (proxy != null) {
      registerBreakpoint(proxy, initUI)
    }
  }

  fun registerBreakpoint(breakpoint: XLineBreakpointProxy, initUI: Boolean) {
    if (initUI) {
      updateBreakpointNow(breakpoint)
    }
    val fileUrl = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
    log.debug { "Register line breakpoint ${breakpoint.id} ${breakpoint.javaClass.simpleName}: $fileUrl" }
    myBreakpoints.putValue(fileUrl, breakpoint)
  }

  fun unregisterBreakpoint(breakpoint: XLineBreakpointProxy) {
    val fileUrl = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
    val removed = myBreakpoints.remove(fileUrl, breakpoint)
    log.debug { "Unregister line breakpoint ${breakpoint.id} [removed=$removed] ${breakpoint.javaClass.simpleName}: $fileUrl" }
  }

  override fun getDocumentBreakpointProxies(document: Document): Collection<XLineBreakpointProxy> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    return myBreakpoints[file.url]
  }

  @TestOnly
  override fun getAllBreakpoints(): Collection<XLineBreakpointProxy> {
    return myBreakpoints.values()
  }

  @RequiresEdt
  private fun updateBreakpoints(document: Document) {
    val breakpoints = getDocumentBreakpointProxies(document)

    if (breakpoints.isEmpty() || ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    SlowOperations.knownIssue("IJPL-162343").use {
      breakpoints.forEach { it.updatePosition() }
    }
    cleanUpBreakpoints(document)
  }

  private fun cleanUpBreakpoints(document: Document) {
    val breakpoints = getDocumentBreakpointProxies(document)
    val file = FileDocumentManager.getInstance().getFile(document)
    val valid = mutableListOf<XLineBreakpointProxy>()
    val invalid = mutableListOf<XLineBreakpointProxy>()
    for (breakpoint in breakpoints) {
      val highlighter = breakpoint.getHighlighter()
      if (highlighter == null) {
        // Breakpoint is uninitialized yet
        continue
      }
      if (highlighter.isValid) {
        valid.add(breakpoint)
      }
      else {
        invalid.add(breakpoint)
      }
    }
    removeInvalidBreakpoints(valid, invalid, document, file)
    // Check if two or more breakpoints occurred at the same position and remove duplicates.
    val areInlineBreakpoints = XDebuggerUtil.areInlineBreakpointsEnabled(FileDocumentManager.getInstance().getFile(document))
    val duplicates = valid
      .groupBy { b ->
        if (areInlineBreakpoints) {
          // We cannot show multiple breakpoints of the same type at the same position.
          // Note that highlightRange might be null, so we still have to add line as an identity element.
          SlowOperations.knownIssue("IJPL-162343").use {
            val startOffset = when (val range = b.getHighlightRange()) {
              is XLineBreakpointHighlighterRange.Available -> range.range?.startOffset
              is XLineBreakpointHighlighterRange.Unavailable -> {
                scheduleBreakpointsCleanUp(document)
                return
              }
            }
            listOf(b.type, b.getLine(), startOffset, b.getPlacement())
          }
        }
        else {
          listOf(b.getLine(), b.getPlacement())
        }
      }
      .values
      .filter { it.size > 1 }
      .flatMap { it.drop(1) }
    removeBreakpoints(duplicates)
  }

  private fun XLineBreakpointManager.removeInvalidBreakpoints(
    valid: MutableList<XLineBreakpointProxy>,
    invalid: MutableList<XLineBreakpointProxy>,
    document: Document,
    file: VirtualFile?,
  ) {
    // Inter-line breakpoints aren't, conceptually, tied to a line; they're in-between the lines,
    // so, if a line numbered N is removed, it's natural for the inter-line breakpoint between lines N-1 and N
    // to re-attach itself to the new line N
    val saveCandidates = invalid.mapNotNull { it.createInterLineSaveCandidate(document, file) }
    val possiblySaveableInterLineBreakpoints = saveCandidates.mapTo(mutableSetOf()) { it.breakpoint }
    removeBreakpoints(invalid - possiblySaveableInterLineBreakpoints)
    saveOrRemoveInterLineBreakpointsAsync(
      saveCandidates = saveCandidates,
      occupiedLines = valid
        .filter { it.getPlacement() == XLineBreakpointVerticalPlacement.INTER_LINE }
        .mapTo(mutableSetOf()) { it.getLine() },
      )
  }

  private data class InterLineSaveCandidateBreakpoint(
    val breakpoint: XLineBreakpointProxy,
    val file: VirtualFile,
    val line: Int,
  )

  private fun XLineBreakpointProxy.createInterLineSaveCandidate(
    document: Document,
    file: VirtualFile?,
  ): InterLineSaveCandidateBreakpoint? {
    if (file == null || getPlacement() != XLineBreakpointVerticalPlacement.INTER_LINE) {
      return null
    }

    // For inter-line breakpoints, deleting the line under the marker shifts the next line
    // into the same line index stored on the breakpoint.
    val candidateLine = getLine()
    if (candidateLine !in 0 until document.lineCount) {
      return null
    }

    return InterLineSaveCandidateBreakpoint(this, file, candidateLine)
  }

  private fun saveOrRemoveInterLineBreakpointsAsync(
    saveCandidates: List<InterLineSaveCandidateBreakpoint>,
    occupiedLines: MutableSet<Int>,
  ) {
    if (saveCandidates.isEmpty()) {
      return
    }

    cs.launch(Dispatchers.EDT) {
      saveOrRemoveInterLineBreakpoints(saveCandidates, occupiedLines)
    }
  }

  private suspend fun saveOrRemoveInterLineBreakpoints(
    candidates: List<InterLineSaveCandidateBreakpoint>,
    occupiedLines: MutableSet<Int>,
  ) {
    val toRemove = candidates.filterNot { it.shouldMoveToNextLine(occupiedLines) }.map { it.breakpoint }
    removeBreakpoints(toRemove)

    val savedBreakpoints = candidates.mapTo(mutableSetOf()) { it.breakpoint } - toRemove.toSet()
    for (breakpoint in savedBreakpoints) {
      updateBreakpointNow(breakpoint)
    }
  }

  private suspend fun InterLineSaveCandidateBreakpoint.shouldMoveToNextLine(
    occupiedLines: Set<Int>,
  ): Boolean {
    return line !in occupiedLines && breakpoint.type.canPutAt(file, line, project)
  }

  private fun removeBreakpoints(toRemove: Collection<XBreakpointProxy>?) {
    for (breakpoint in toRemove.orEmpty()) {
      manager.removeBreakpoint(breakpoint)
    }
  }

  private fun isImmediateUiUpdateAllowed(): Boolean {
    return EDT.isCurrentThreadEdt() && immediateUiUpdateOnEdtAllowed
  }

  private inline fun withImmediateUiUpdateDisabled(block: () -> Unit) {
    if (!EDT.isCurrentThreadEdt()) {
      block()
      return
    }
    immediateUiUpdateOnEdtAllowed = false
    try {
      block()
    }
    finally {
      immediateUiUpdateOnEdtAllowed = true
    }
  }

  override fun breakpointChanged(breakpoint: XLightLineBreakpointProxy) {
    if (isImmediateUiUpdateAllowed()) {
      updateBreakpointNow(breakpoint)
    }
    else {
      queueBreakpointUpdate(breakpoint)
    }
  }

  @JvmOverloads
  fun queueBreakpointUpdate(slave: XBreakpoint<*>?, callOnUpdate: Runnable? = null) {
    if (slave == null) return
    val proxy = XDebuggerEntityConverter.asProxy(slave) as? XLineBreakpointProxy ?: return
    queueBreakpointUpdate(proxy, callOnUpdate)
  }

  @Deprecated("Use queueBreakpointUpdateCallback(XLightLineBreakpointProxy, Runnable)")
  fun queueBreakpointUpdateCallback(breakpoint: XLineBreakpoint<*>?, callback: Runnable) {
    breakpointUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        callback.run()
      }
    })
  }

  override fun queueBreakpointUpdateCallback(breakpoint: XLightLineBreakpointProxy, callback: Runnable) {
    breakpointUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        callback.run()
      }
    })
  }

  // Skip waiting 300ms in myBreakpointsUpdateQueue (good for sync updates like enable/disable or create new breakpoint)
  private fun updateBreakpointNow(breakpoint: XLightLineBreakpointProxy) {
    queueBreakpointUpdate(breakpoint)
    breakpointUpdateQueue.sendFlush()
  }

  private fun queueBreakpointUpdate(breakpoint: XLightLineBreakpointProxy, callOnUpdate: Runnable? = null) {
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
        for (breakpoint in myBreakpoints.values()) {
          breakpoint.doUpdateUI()
        }
      }
    })
    // skip waiting
    breakpointUpdateQueue.sendFlush()
  }

  private inner class MyDocumentListener : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(e: DocumentEvent) {
      if (processDocumentChange(e.document)) {
        InlineBreakpointInlayManager.getInstance(project).redrawDocument(e)
      }
    }

    override fun bulkUpdateFinished(document: Document) {
      if (processDocumentChange(document)) {
        InlineBreakpointInlayManager.getInstance(project).redrawDocument(document)
      }
    }

    private fun processDocumentChange(document: Document): Boolean {
      val breakpoints = getDocumentBreakpointProxies(document)
      if (breakpoints.isEmpty()) return false

      // fastUpdatePosition leads to the breakpointChanged call,
      // but for the document update we do not need immediate UI update
      withImmediateUiUpdateDisabled {
        // Update position immediately to avoid races with doUpdateUI
        // We must mark the range as dirty so that no other asynchronous repaint makes breakpoint presentation incorrect.
        breakpoints.forEach { it.fastUpdatePosition() }
      }

      scheduleDocumentUpdate(document)
      return true
    }
  }

  private fun scheduleDocumentUpdate(document: Document) {
    breakpointUpdateQueue.queue(object : Update("update" to document) {
      override fun run() {
        ApplicationManager.getApplication().invokeLater {
          updateBreakpoints(document)
        }
      }
    })
  }

  private fun scheduleBreakpointsCleanUp(document: Document) {
    breakpointUpdateQueue.queue(object : Update("clean up" to document) {
      override fun run() {
        ApplicationManager.getApplication().invokeLater {
          cleanUpBreakpoints(document)
        }
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
      if ((mouseEvent.isPopupTrigger || mouseEvent.isControlDown)
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
      // Use inter-line detection for click handling; configs are calculated asynchronously by the gutter
      val hitResult = EditorUtil.yToLogicalLineWithInterLineDetection(editor, mouseEvent)
      val line = hitResult.line
      val isInterLine = hitResult.isBetweenLines
      val file = FileDocumentManager.getInstance().getFile(document)
      if (line >= 0 && DocumentUtil.isValidLine(line, document) && file != null) {
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)
        if (action == null) throw AssertionError("'" + IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT + "' action not found")
        val baseContext = DataManager.getInstance().getDataContext(mouseEvent.component)
        val dataContext = SimpleDataContext.builder().apply {
          setParent(baseContext)
          add(BREAKPOINT_LINE_KEY, line)
          add(INTER_LINE_BREAKPOINT_KEY, isInterLine)
          if (hitResult is BreakpointArea.InterLine) {
            add(InterLineBreakpointProperties.KEY, hitResult.configuration.breakpointProperties)
          }
        }.build()
        val event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.EDITOR_GUTTER, dataContext)
        // TODO IJPL-185322 Introduce a better way to handle actions in the frontend
        // TODO We actually want to call the action directly, but dispatch it on frontend if possible
        if (SplitDebuggerMode.isSplitDebugger()) {
          // Call handler directly so that it will be called on frontend
          val handler = ToggleLineBreakpointAction.ourHandler
          if (handler.isEnabled(project, event)) {
            handler.perform(project, event)
            // statistics reporting
            ActionsCollectorImpl.onAfterActionInvoked(action, event, AnActionResult.PERFORMED)
          }
        }
        else {
          // Cannot call the handler directly in case of LUX split.
          // Call the action so that it is delegated to the backend action.
          ActionUtil.performAction(action, event)
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
    @JvmField
    val INTER_LINE_BREAKPOINT_KEY: DataKey<Boolean> = DataKey.create("xdebugger.breakpoint.interline")
    @JvmField
    val LOG_EXPRESSION: Key<String> = Key.create("xdebugger.breakpoint.logExpression")
  }
}
