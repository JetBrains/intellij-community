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
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.LinuxIdeMenuBar.Companion.doBindAppMenuOfParent
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.ui.*
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.childScope
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.*

open class FrameWrapper @JvmOverloads constructor(private var project: Project?,
                                                  @param:NonNls protected open val dimensionKey: String? = null,
                                                  private val isDialog: Boolean = false,
                                                  @NlsContexts.DialogTitle var title: String = "",
                                                  open var component: JComponent? = null) : Disposable, DataProvider {
  open var preferredFocusedComponent: JComponent? = null
  private var images: List<Image> = emptyList()
  private var isCloseOnEsc = false
  private var onCloseHandler: BooleanGetter? = null
  private var frame: Window? = null
  private var isDisposing = false

  var isDisposed: Boolean = false
    private set

  protected var statusBar: StatusBar? = null

  init {
    if (project != null) {
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosing(project: Project) {
          if (project === this@FrameWrapper.project) {
            close()
          }
        }
      })
    }
  }

  @Deprecated("Pass project to constructor")
  fun setProject(@Suppress("UNUSED_PARAMETER") project: Project) {
  }

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
    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent) {
        close()
      }
    })

    UIUtil.decorateWindowHeader((frame as RootPaneContainer).rootPane)
    if (frame is JFrame) {
      val handlerProvider = Supplier { FullScreenSupport.NEW.apply("com.intellij.ui.mac.MacFullScreenSupport") }
      ToolbarUtil.setTransparentTitleBar(frame, frame.rootPane, handlerProvider) { runnable ->
        Disposer.register(this, Disposable { runnable.run() })
      }
    }

    val focusListener = object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) {
        val focusManager = IdeFocusManager.getInstance(project)
        val toFocus = preferredFocusedComponent ?: focusManager.getLastFocusedFor(e.window) ?: focusManager.getFocusTargetFor(component!!)
        toFocus?.requestFocusInWindow()
      }
    }
    frame.addWindowListener(focusListener)

    if (RegistryManager.getInstance().`is`("ide.perProjectModality")) {
      frame.isAlwaysOnTop = true
    }
    Disposer.register(this, Disposable {
      frame.removeWindowListener(focusListener)
    })

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

    if (SystemInfoRt.isLinux && frame is JFrame && GlobalMenuLinux.isAvailable()) {
      val parentFrame = WindowManager.getInstance().getFrame(project)
      if (parentFrame != null) {
        doBindAppMenuOfParent(frame, parentFrame)
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
    if (isDisposed || (onCloseHandler != null && !onCloseHandler!!.get())) {
      return
    }

    // if you remove this line problems will start happen on Mac OS X
    // 2 projects opened, call Cmd+D on the second opened project and then Esc.
    // Weird situation: 2nd IdeFrame will be active, but focus will be somewhere inside the 1st IdeFrame
    // App is unusable until Cmd+Tab, Cmd+tab
    frame?.isVisible = false
    Disposer.dispose(this)
  }

  override fun dispose() {
    if (isDisposed) {
      return
    }

    val frame = frame
    this.frame = null
    preferredFocusedComponent = null
    project = null
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

  protected open fun getNorthExtension(key: String?): JComponent? = null

  override fun getData(@NonNls dataId: String): Any? {
    return if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
  }

  private fun getDataInner(@NonNls dataId: String): Any? {
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

  fun setOnCloseHandler(value: BooleanGetter?) {
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

  private class MyJFrame(private var owner: FrameWrapper, private val parent: IdeFrame) : JFrame(), DataProvider, IdeFrame.Child, IdeFrameEx {
    private var frameTitle: String? = null
    private var fileTitle: String? = null
    private var file: Path? = null

    init {
      FrameState.setFrameStateListener(this)
      glassPane = IdeGlassPaneImpl(rootPane = getRootPane(), installPainters = true)
      if (SystemInfoRt.isMac && !(SystemInfo.isMacSystemMenu && java.lang.Boolean.getBoolean("mac.system.menu.singleton"))) {
        @Suppress("DEPRECATION")
        jMenuBar = createMenuBar(coroutineScope = ApplicationManager.getApplication().coroutineScope.childScope(), this)

      }
      MouseGestureManager.getInstance().add(this)
      focusTraversalPolicy = IdeFocusTraversalPolicy()
    }

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

    override fun getBalloonLayout(): BalloonLayout? {
      return null
    }

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
  }

  fun setLocation(location: Point) {
    getFrame().location = location
  }

  fun setSize(size: Dimension?) {
    getFrame().size = size
  }

  private class MyJDialog(private val owner: FrameWrapper, private val parent: IdeFrame) : JDialog(ComponentUtil.getWindow(parent.component)), DataProvider, IdeFrame.Child {
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
  }
}

private fun getWindowStateService(project: Project?): WindowStateService {
  return if (project == null) WindowStateService.getInstance() else WindowStateService.getInstance(project)
}
