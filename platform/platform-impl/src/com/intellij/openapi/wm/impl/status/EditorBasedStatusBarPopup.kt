// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package com.intellij.openapi.wm.impl.status

import com.intellij.ide.DataManager
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.StatusBarPopupShown
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.progress.runBlockingModalWithRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.Multiframe
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.TextPanel.WithIconAndArrows
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.ui.ClickListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
abstract class EditorBasedStatusBarPopup(
  project: Project,
  private val isWriteableFileRequired: Boolean,
  protected val scope: CoroutineScope,
) : EditorBasedWidget(project), Multiframe, CustomStatusBarWidget {
  @Suppress("DEPRECATION")
  constructor(project: Project, isWriteableFileRequired: Boolean) : this(project, isWriteableFileRequired, (project as ComponentManagerEx).getCoroutineScope())

  private val component: Lazy<JPanel> = lazy {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      EDT.assertIsEdt()
    }
    val component = createComponent()
    component.isVisible = false

    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        update()
        StatusBarPopupShown.log(project, this@EditorBasedStatusBarPopup.javaClass)
        showPopup(e)
        return true
      }
    }.installOn(component, true)

    component
  }

  var isActionEnabled: Boolean = false
    protected set

  private val update = MutableSharedFlow<Runnable?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // store editor here to avoid expensive and EDT-only getSelectedEditor() retrievals
  @Volatile
  private var editor = WeakReference<Editor?>(null)

  init {
    scope.launch {
      update
        .debounce(300.milliseconds)
        .collectLatest(::doUpdate)
    }.cancelOnDispose(this)

    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        fileChanged(file)
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        fileChanged(file)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          return
        }

        val newFile = event.newFile
        val fileEditor = if (newFile == null) null else FileEditorManager.getInstance(project).getSelectedEditor(newFile)
        editor = WeakReference((fileEditor as? TextEditor)?.editor)
        fileChanged(newFile)
      }
    })

    registerCustomListeners(myConnection)
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        val document = e.document
        updateForDocument(document)
      }
    }, this)
    if (isWriteableFileRequired) {
      myConnection.subscribe(VirtualFileManager.VFS_CHANGES, BulkVirtualFileListenerAdapter(object : VirtualFileListener {
        override fun propertyChanged(event: VirtualFilePropertyEvent) {
          if (event.propertyName == VirtualFile.PROP_WRITABLE) {
            updateForFile(event.file)
          }
        }
      }))
    }
  }

  private suspend fun doUpdate(finishUpdate: Runnable?) {
    val file = getSelectedFile()
    val state = readAction {
      getWidgetState(file?.takeIf { it.isValid })
    }
    if (state != WidgetState.NO_CHANGE) {
      withContext(Dispatchers.EDT) {
        applyUpdate(finishUpdate = finishUpdate, file = file, state = state)
      }
    }
  }

  protected open fun createComponent(): JPanel = WithIconAndArrows()

  @ApiStatus.Internal
  fun setEditor(editor: Editor?) {
    this.editor = WeakReference(editor)
  }

  fun selectionChanged(newFile: VirtualFile?) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val fileEditor = if (newFile == null) null else FileEditorManager.getInstance(project).getSelectedEditor(newFile)
    editor = WeakReference((fileEditor as? TextEditor)?.editor)
    fileChanged(newFile)
  }

  private fun fileChanged(newFile: VirtualFile?) {
    handleFileChange(newFile)
    update()
  }

  protected open fun handleFileChange(file: VirtualFile?) {}

  override fun copy(): StatusBarWidget = createInstance(project)

  override fun getPresentation(): WidgetPresentation? = null

  override fun install(statusBar: StatusBar) {
    super<EditorBasedWidget>.install(statusBar)

    setEditor(getEditor())
    update()
  }

  protected open fun updateForDocument(document: Document?) {
    val selectedEditor = editor.get()
    if (document == null || !(selectedEditor == null || selectedEditor.document !== document)) {
      update()
    }
  }

  protected open fun updateForFile(file: VirtualFile?) {
    if (file == null) {
      update()
    }
    else {
      updateForDocument(FileDocumentManager.getInstance().getCachedDocument(file))
    }
  }

  private fun showPopup(e: MouseEvent) {
    if (!isActionEnabled) {
      // do not show popup
      return
    }

    val dataContext = context
    val popup = createPopup(dataContext) ?: return
    val dimension = popup.content.preferredSize
    val at = Point(0, -dimension.height)
    popup.show(RelativePoint(e.component, at))
    // destroy popup on unexpected project close
    Disposer.register(this, popup)
  }

  protected val context: DataContext
    get() {
      val editor = getEditor()
      return if (editor == null) {
        DataManager.getInstance().getDataContext(myStatusBar as Component)
      }
      else {
        EditorUtil.getEditorDataContext(editor)
      }
    }

  override fun getComponent(): JComponent = component.value

  protected open val isEmpty: Boolean
    get() {
      return (component.value as? WithIconAndArrows)?.let { textPanel ->
        textPanel.text.isNullOrEmpty() && !textPanel.hasIcon()
      } ?: false
    }

  protected open fun updateComponent(state: WidgetState) {
    val component = component.value
    component.toolTipText = state.toolTip
    (component as? WithIconAndArrows)?.let { textPanel ->
      textPanel.setTextAlignment(Component.CENTER_ALIGNMENT)
      textPanel.icon = state.icon
      textPanel.text = state.text
    }
  }

  @TestOnly
  fun updateInTests(immediately: Boolean) {
    if (immediately) {
      runBlockingModalWithRawProgressReporter(ModalTaskOwner.guess(), "") {
        doUpdate(null)
      }
    }
    else {
      update()
    }
    drainRequestsInTest()
    if (immediately) {
      // for widgets with background activities, the first flush() adds handlers to be called
      drainRequestsInTest()
    }
  }

  @TestOnly
  private fun drainRequestsInTest() {
    runBlockingModalWithRawProgressReporter(ModalTaskOwner.guess(), "") {
      val replayCache = update.replayCache
      @Suppress("OPT_IN_USAGE")
      update.resetReplayCache()
      for (runnable in replayCache) {
        doUpdate(runnable)
      }
    }

    UIUtil.dispatchAllInvocationEvents()
  }

  @TestOnly
  fun flushUpdateInTests() {
    while (!update.replayCache.isEmpty()) {
      drainRequestsInTest()
    }
  }

  fun update() {
    update(null)
  }

  open fun update(finishUpdate: Runnable?) {
    check(update.tryEmit(finishUpdate))
  }

  private fun applyUpdate(finishUpdate: Runnable?, file: VirtualFile?, state: WidgetState) {
    if (state === WidgetState.NO_CHANGE_MAKE_VISIBLE) {
      component.value.isVisible = true
      return
    }
    if (state === WidgetState.HIDDEN) {
      if (component.isInitialized()) {
        component.value.isVisible = false
      }
      return
    }

    val component = component.value
    component.isVisible = true
    isActionEnabled = state.isActionEnabled && isEnabledForFile(file)
    component.isEnabled = isActionEnabled
    updateComponent(state)
    if (myStatusBar != null && !component.isValid) {
      myStatusBar!!.updateWidget(ID())
    }
    finishUpdate?.run()
    afterVisibleUpdate(state)
  }

  protected open fun afterVisibleUpdate(state: WidgetState) {}

  protected open class WidgetState(val toolTip: @NlsContexts.Tooltip String?,
                                   val text: @NlsContexts.StatusBarText String?,
                                   val isActionEnabled: Boolean) {
    var icon: Icon? = null

    private constructor() : this("", "", false)

    companion object {
      /**
       * Return this state if you want to hide the widget
       */
      @JvmField
      val HIDDEN: WidgetState = WidgetState()

      /**
       * Return this state if you don't want to change widget presentation
       */
      @JvmField
      val NO_CHANGE: WidgetState = WidgetState()

      /**
       * Return this state if you want to show widget in its previous state
       * but without updating its content
       */
      @JvmField
      val NO_CHANGE_MAKE_VISIBLE: WidgetState = WidgetState()

      /**
       * Returns a special state for dumb mode (when indexes are not ready).
       * Your widget should show this state if it depends on indexes, when DumbService.isDumb is true.
       *
       *
       * Use myConnection.subscribe(DumbService.DUMB_MODE, your_listener) inside registerCustomListeners,
       * and call update() inside listener callbacks, to refresh your widget state when indexes are loaded
       */
      @JvmStatic
      fun getDumbModeState(name: @Nls String?, widgetPrefix: @NlsContexts.StatusBarText String?): WidgetState {
        // todo: update accordingly to UX-252
        return WidgetState(toolTip = ActionUtil.getUnavailableMessage(name!!, false),
                           text = widgetPrefix + IndexingBundle.message("progress.indexing.updating"),
                           isActionEnabled = false)
      }
    }
  }

  @RequiresBackgroundThread
  protected abstract fun getWidgetState(file: VirtualFile?): WidgetState

  /**
   * @param file result of [EditorBasedStatusBarPopup.getSelectedFile]
   * @return false if widget should be disabled for `file`
   * even if [EditorBasedStatusBarPopup.getWidgetState] returned [WidgetState.isActionEnabled].
   */
  protected open fun isEnabledForFile(file: VirtualFile?): Boolean = file == null || !isWriteableFileRequired || file.isWritable

  protected abstract fun createPopup(context: DataContext): ListPopup?

  @Deprecated(message="Use registerCustomListeners(MessageBusConnection)")
  protected open fun registerCustomListeners() {}

  override fun registerCustomListeners(connection: MessageBusConnection) {
    @Suppress("DEPRECATION")
    registerCustomListeners()
  }

  protected abstract fun createInstance(project: Project): StatusBarWidget
}