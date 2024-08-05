// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.MouseGestureManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.RootPaneUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.function.BooleanSupplier
import javax.swing.*

open class FrameWrapper @JvmOverloads constructor(private val project: Project?,
                                                  @param:NonNls protected open val dimensionKey: String? = null,
                                                  private val isDialog: Boolean = false,
                                                  @NlsContexts.DialogTitle var title: String = "",
                                                  open var component: JComponent? = null,
                                                  @JvmField protected val coroutineScope: CoroutineScope? = null) : Disposable, DataProvider {
  open var preferredFocusedComponent: JComponent? = null
  private var images: List<Image> = emptyList()
  private var isCloseOnEsc = false
  private var onCloseHandler: BooleanSupplier? = null
  private var frame: Window? = null
  @JvmField
  internal var isDisposing: Boolean = false

  var isDisposed: Boolean = false
    private set

  @JvmField
  internal var statusBar: StatusBar? = null

  init {
    if (project != null) {
      val connection = if (coroutineScope == null) {
        @Suppress("LeakingThis")
        ApplicationManager.getApplication().messageBus.connect(this)
      }
      else {
        ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      }
      connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosing(project: Project) {
          if (project === this@FrameWrapper.project) {
            close()
          }
        }
      })
    }
  }

  @Obsolete
  protected fun getStatusBar(): StatusBar? = statusBar

  open fun show() {
    show(true)
  }

  protected open val isDockWindow: Boolean
    get() = false

  fun createContents() {
    val frame = getFrame()
    if (frame is JFrame) {
      frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
    }
    else {
      (frame as JDialog).defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
    }

    ComponentUtil.decorateWindowHeader((frame as RootPaneContainer).rootPane)
    if (frame is JFrame) {
      ToolbarService.getInstance().setTransparentTitleBar(
        window = frame,
        rootPane = frame.rootPane,
        handlerProvider = { FullScreenSupport.NEW.apply("com.intellij.ui.mac.MacFullScreenSupport") },
        onDispose = { runnable ->
          executeOnDispose { runnable.run() }
        },
      )
    }

    val windowListener = object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) {
        val focusManager = IdeFocusManager.getInstance(project)
        val toFocus = preferredFocusedComponent ?: focusManager.getLastFocusedFor(e.window) ?: focusManager.getFocusTargetFor(component!!)
        toFocus?.requestFocusInWindow()
      }

      override fun windowClosing(e: WindowEvent) {
        close()
      }
    }
    frame.addWindowListener(windowListener)
    executeOnDispose {
      frame.removeWindowListener(windowListener)
    }

    if (Registry.`is`("ide.perProjectModality", false)) {
      frame.isAlwaysOnTop = true
    }

    if (isCloseOnEsc) {
      addCloseOnEsc(frame as RootPaneContainer)
    }

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      component?.let {
        component = CustomFrameDialogContent.getCustomContentHolder(window = frame, content = it, isForDockContainerProvider = isDockWindow)
      }
    }

    frame.contentPane.add(component!!, BorderLayout.CENTER)
    if (frame is JFrame) {
      frame.title = title
    }
    else {
      (frame as JDialog).title = title
    }

    if (images.isEmpty()) {
      updateAppWindowIcon(frame)
    }
    else {
      // unwrap the image before setting as frame's icon
      frame.iconImages = images.map { ImageUtil.toBufferedImage(it) }
    }
  }

  private fun executeOnDispose(task: () -> Unit) {
    if (coroutineScope == null) {
      Disposer.register(this, Disposable {
        task()
      })
    }
    else {
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        task()
      }
    }
  }

  fun show(restoreBounds: Boolean) {
    createContents()

    val frame = getFrame()

    val state = dimensionKey?.let { getWindowStateService(project).getState(it, frame) }
    if (restoreBounds) {
      loadFrameState(state)
    }

    if (SystemInfoRt.isMac) {
      TouchbarSupport.showWindowActions(this, frame)
    }
    frame.isVisible = true
  }

  fun close() {
    if (isDisposed || (onCloseHandler != null && !onCloseHandler!!.asBoolean)) {
      return
    }

    // The order matters here: dispose() may need to access some properties of the still visible frame,
    // for example, windowed tool windows need to remember their bounds,
    // and that must be done while the frame is still visible (otherwise the bounds are 0,0,0,0).
    Disposer.dispose(this)
    // The following no longer seems to be reproducible, but keeping that line won't hurt anyway:
    // if you remove this line, problems will start happen on Mac OS X
    // 2 project opening, call Cmd+D on the second opened project and then Esc.
    // Weird situation: 2nd IdeFrame will be active, but focus will be somewhere inside the 1st IdeFrame
    // App is unusable until Cmd+Tab, Cmd+tab
    frame?.isVisible = false
  }

  override fun dispose() {
    coroutineScope?.cancel()

    if (isDisposed) {
      return
    }

    val frame = frame
    this.frame = null
    preferredFocusedComponent = null
    component = null
    images = emptyList()
    isDisposed = true

    if (frame != null) {
      frame.isVisible = false
      val rootPane = (frame as RootPaneContainer).rootPane
      frame.removeAll()
      if (frame is IdeFrame) {
        MouseGestureManager.getInstance().remove(frame)
      }
      frame.dispose()
      DialogWrapper.cleanupRootPane(rootPane)
      DialogWrapper.cleanupWindowListeners(frame)
    }
  }

  private fun addCloseOnEsc(frame: RootPaneContainer) {
    val rootPane = frame.rootPane
    val closeAction = ActionListener {
      if (!PopupUtil.handleEscKeyEvent()) {
        close()
      }
    }
    rootPane.registerKeyboardAction(closeAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
    ActionUtil.registerForEveryKeyboardShortcut(rootPane, closeAction, CommonShortcuts.getCloseActiveWindow())
  }

  fun getFrame(): Window {
    assert(!isDisposed) { "Already disposed!" }
    var result = frame
    if (result == null) {
      val parent = WindowManager.getInstance().getIdeFrame(project)!!
      result = if (isDialog) createJDialog(parent) else createJFrame(parent)
      frame = result
    }
    return result
  }

  val isActive: Boolean
    get() = frame?.isActive == true

  protected open fun createJFrame(parent: IdeFrame): JFrame = MyJFrame(this, parent)

  protected open fun createJDialog(parent: IdeFrame): JDialog = MyJDialog(this, parent)

  internal open fun getNorthExtension(key: String?): JComponent? = null

  override fun getData(@NonNls dataId: String): Any? {
    return if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
  }

  internal fun getDataInner(@NonNls dataId: String): Any? {
    return when {
      CommonDataKeys.PROJECT.`is`(dataId) -> project
      else -> getData(dataId)
    }
  }

  fun closeOnEsc() {
    isCloseOnEsc = true
  }

  fun setImage(image: Image?) {
    setImages(listOfNotNull(image))
  }

  fun setImages(value: List<Image>?) {
    images = value ?: emptyList()
  }

  fun setOnCloseHandler(value: BooleanSupplier?) {
    onCloseHandler = value
  }

  protected open fun loadFrameState(state: WindowState?) {
    val frame = getFrame()
    if (state == null) {
      val ideFrame = WindowManagerEx.getInstanceEx().getIdeFrame(project)
      if (ideFrame != null) {
        frame.bounds = ideFrame.suggestChildFrameBounds()
      }
    }
    else {
      state.applyTo(frame)
    }
    (frame as RootPaneContainer).rootPane.revalidate()
  }

  fun setLocation(location: Point) {
    getFrame().location = location
  }

  fun setSize(size: Dimension?) {
    getFrame().size = size
  }
}

private class MyJFrame(private var owner: FrameWrapper, private val parent: IdeFrame) : JFrame(), DataProvider, IdeFrame.Child, IdeFrameEx, DisposableWindow {
  private var frameTitle: String? = null
  private var fileTitle: String? = null
  private var file: Path? = null

  init {
    FrameState.setFrameStateListener(this)
    glassPane = IdeGlassPaneImpl(rootPane = getRootPane(), installPainters = true)
    if (SystemInfoRt.isMac && !Menu.isJbScreenMenuEnabled()) {
      jMenuBar = RootPaneUtil.createMenuBar(coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope.childScope(),
                                            frame = this,
                                            customMenuGroup = null)
    }
    MouseGestureManager.getInstance().add(this)
    focusTraversalPolicy = IdeFocusTraversalPolicy()
  }

  override fun isWindowDisposed(): Boolean = owner.isDisposed

  override fun isInFullScreen() = false

  override fun toggleFullScreen(state: Boolean): Job = CompletableDeferred(value = Unit)

  override fun addNotify() {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      CustomHeader.enableCustomHeader(this)
    }
    super.addNotify()
  }

  override fun getComponent(): JComponent = getRootPane()

  override fun getStatusBar(): StatusBar? {
    return (if (owner.isDisposing) null else owner.statusBar) ?: parent.statusBar
  }

  override fun suggestChildFrameBounds(): Rectangle = parent.suggestChildFrameBounds()

  override fun getProject() = parent.project

  override fun notifyProjectActivation() = parent.notifyProjectActivation()

  override fun setFrameTitle(title: String) {
    frameTitle = title
    updateTitle()
  }

  override fun setFileTitle(fileTitle: String?, ioFile: Path?) {
    this.fileTitle = fileTitle
    file = ioFile
    updateTitle()
  }

  override fun getNorthExtension(key: String): JComponent? = owner.getNorthExtension(key)

  override fun getBalloonLayout(): BalloonLayout? = null

  private fun updateTitle() {
    if (AdvancedSettings.getBoolean("ide.show.fileType.icon.in.titleBar")) {
      // this property requires java.io.File
      rootPane.putClientProperty("Window.documentFile", file?.toFile())
    }

    val builder = StringBuilder()
    ProjectFrameHelper.appendTitlePart(builder, frameTitle)
    ProjectFrameHelper.appendTitlePart(builder, fileTitle)
    title = builder.toString()
    if (title.isNullOrEmpty()) {
      project?.let { title = FrameTitleBuilder.getInstance().getProjectTitle(it) }
    }
  }

  override fun dispose() {
    val owner = owner
    if (owner.isDisposing) {
      return
    }

    owner.isDisposing = true
    // must be called in addition to the `dispose`, otherwise not removed from `Window.allWindows` list.
    isVisible = false
    Disposer.dispose(owner)
    super.dispose()
    rootPane = null
    menuBar = null
  }

  override fun getData(dataId: String): Any? {
    return when {
      IdeFrame.KEY.`is`(dataId) -> this
      owner.isDisposing -> null
      else -> owner.getDataInner(dataId)
    }
  }

  override fun paint(g: Graphics) {
    setupAntialiasing(g)
    super.paint(g)
  }

  @Suppress("OVERRIDE_DEPRECATION") // need this just for logging
  override fun reshape(x: Int, y: Int, width: Int, height: Int) {
    if (LOG.isTraceEnabled) {
      LOG.trace(Throwable("FrameWrapper frame bounds changed to $x, $y, $width, $height"))
    }
    super.reshape(x, y, width, height)
  }
}

private class MyJDialog(private val owner: FrameWrapper, private val parent: IdeFrame) :
  JDialog(ComponentUtil.getWindow(parent.component)), DataProvider, IdeFrame.Child, DisposableWindow {
  override fun getComponent(): JComponent = getRootPane()

  override fun getStatusBar(): StatusBar? = null

  override fun getBalloonLayout(): BalloonLayout? = null

  override fun suggestChildFrameBounds(): Rectangle = parent.suggestChildFrameBounds()

  override fun getProject(): Project? = parent.project

  override fun notifyProjectActivation() = parent.notifyProjectActivation()

  init {
    glassPane = IdeGlassPaneImpl(getRootPane())
    getRootPane().putClientProperty("Window.style", "small")
    background = UIUtil.getPanelBackground()
    MouseGestureManager.getInstance().add(this)
    focusTraversalPolicy = IdeFocusTraversalPolicy()
  }

  override fun setFrameTitle(title: String) {
    setTitle(title)
  }

  override fun dispose() {
    if (owner.isDisposing) {
      return
    }

    owner.isDisposing = true
    Disposer.dispose(owner)
    super.dispose()
    rootPane = null
  }

  override fun isWindowDisposed(): Boolean = owner.isDisposed

  override fun getData(dataId: String): Any? {
    return when {
      IdeFrame.KEY.`is`(dataId) -> this
      owner.isDisposing -> null
      else -> owner.getDataInner(dataId)
    }
  }

  override fun paint(g: Graphics) {
    setupAntialiasing(g)
    super.paint(g)
  }

  @Suppress("OVERRIDE_DEPRECATION") // need this just for logging
  override fun reshape(x: Int, y: Int, width: Int, height: Int) {
    if (LOG.isTraceEnabled) {
      LOG.trace(Throwable("FrameWrapper dialog bounds changed to $x, $y, $width, $height"))
    }
    super.reshape(x, y, width, height)
  }
}

private fun getWindowStateService(project: Project?): WindowStateService {
  return if (project == null) WindowStateService.getInstance() else WindowStateService.getInstance(project)
}

private val LOG = logger<FrameWrapper>()
