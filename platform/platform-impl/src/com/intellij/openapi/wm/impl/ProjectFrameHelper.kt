// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.MouseGestureManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl.FrameHelper
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.SuperUserStatus.isSuperUser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.accessibility.AccessibleContextAccessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.accessibility.AccessibleContext
import javax.swing.*

private const val INIT_BOUNDS_KEY = "InitBounds"

private val LOG: Logger
  get() = logger<ProjectFrameHelper>()

open class ProjectFrameHelper internal constructor(
  val frame: IdeFrameImpl,
  loadingState: FrameLoadingState? = null,
) : IdeFrameEx, AccessibleContextAccessor, DataProvider, Disposable {
  constructor(frame: IdeFrameImpl) : this(frame = frame, loadingState = null)

  private val isUpdatingTitle = AtomicBoolean()
  private var title: String? = null
  private var fileTitle: String? = null
  private var currentFile: Path? = null
  private var project: Project? = null

  @JvmField
  @Internal
  val rootPane: IdeRootPane

  private var balloonLayout: BalloonLayout? = null
  private val frameDecorator: IdeFrameDecorator?

  // frame can be activated before a project is assigned to it,
  // so we remember the activation time and report it against the assigned project later
  private var activationTimestamp: Long? = null

  init {
    frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
    frame.addWindowListener(WindowCloseListener)

    @Suppress("LeakingThis")
    rootPane = createIdeRootPane(loadingState)
    frame.doSetRootPane(rootPane)
    // NB!: the root pane must be set before decorator, which holds its own client properties in a root pane
    @Suppress("LeakingThis")
    frameDecorator = IdeFrameDecorator.decorate(frame, rootPane.glassPane as IdeGlassPane, this)
    frame.setFrameHelper(object : FrameHelper {
      override fun getData(dataId: String) = this@ProjectFrameHelper.getData(dataId)

      override val accessibleName: String
        get() {
          val builder = StringBuilder()
          project?.let {
            builder.append(it.name)
            builder.append(" - ")
          }
          builder.append(ApplicationNamesInfo.getInstance().fullProductName)
          return builder.toString()
        }

      override val project: Project?
        get() = this@ProjectFrameHelper.project

      override val helper: IdeFrame
        get() = this@ProjectFrameHelper

      override val isInFullScreen: Boolean
        get() = frameDecorator?.isInFullScreen ?: false

      override fun dispose() {
        if (isTemporaryDisposed(frame)) {
          frame.doDispose()
        }
        else {
          Disposer.dispose(this@ProjectFrameHelper)
        }
      }
    })
    if (frameDecorator != null && getReusedFullScreenState()) {
      frameDecorator.setStoredFullScreen()
    }
    frame.background = JBColor.PanelBackground
    rootPane.preInit(isInFullScreen = { isInFullScreen })

    balloonLayout = ActionCenterBalloonLayout(rootPane, JBUI.insets(8))
  }

  companion object {
    @JvmStatic
    fun getFrameHelper(window: Window?): ProjectFrameHelper? {
      if (window == null) {
        return null
      }

      val projectFrame = if (window is IdeFrameImpl) {
        window
      }
      else {
        SwingUtilities.getAncestorOfClass(IdeFrameImpl::class.java, window) as? IdeFrameImpl ?: return null
      }
      return projectFrame.frameHelper?.helper as? ProjectFrameHelper
    }

    val superUserSuffix: String?
      get() = if (!isSuperUser) null else if (SystemInfoRt.isWindows) "Administrator" else "ROOT"

    internal fun appendTitlePart(sb: StringBuilder, s: String?) {
      appendTitlePart(sb, s, " \u2013 ")
    }
  }

  protected open fun createIdeRootPane(loadingState: FrameLoadingState?): IdeRootPane {
    return IdeRootPane(frame = frame, parentDisposable = this, loadingState = loadingState)
  }

  private val isInitialized = AtomicBoolean()

  // purpose of delayed init -
  // to show project frame as early as possible (and start loading of a project too) and use it as project loading "splash"
  // show frame -> start project loading (performed in a pooled thread) -> do UI tasks while project loading
  fun init(): JFrame {
    if (!isInitialized.compareAndSet(false, true)) {
      return frame
    }

    rootPane.createAndConfigureStatusBar(frameHelper = this)
    val frame = frame
    MnemonicHelper.init(frame)
    frame.focusTraversalPolicy = IdeFocusTraversalPolicy()

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfoRt.isMac) {
      frame.iconImage = null
    }
    else {
      if (SystemInfoRt.isLinux) {
        installAppMenuIfNeeded(frame)
      }

      // in production (not from sources) it makes sense only on Linux
      // or on Windows (for products that don't use a native launcher, e.g., MPS)
      updateAppWindowIcon(frame)
    }
    return frame
  }

  fun postInit() {
    (rootPane.glassPane as IdeGlassPaneImpl).installPainters()
    if (SystemInfoRt.isMac) {
      MouseGestureManager.getInstance().add(this)
    }
  }

  override fun getComponent(): JComponent? = frame.rootPane

  override fun getStatusBar(): IdeStatusBarImpl? = rootPane.statusBar

  override fun setFrameTitle(text: String) {
    frame.title = text
  }

  override fun setFileTitle(fileTitle: String?, file: Path?) {
    this.fileTitle = fileTitle
    currentFile = file
    updateTitle(project)
  }

  override fun getNorthExtension(key: String): JComponent? = project?.let { rootPane.findNorthUiComponentByKey(key = key) }

  protected open fun getTitleInfoProviders(): List<TitleInfoProvider> {
    return TitleInfoProvider.EP.extensionList
  }

  suspend fun updateTitle(title: String, project: Project) {
    val titleInfoProviders = getTitleInfoProviders()
    withContext(Dispatchers.EDT) {
      this@ProjectFrameHelper.title = title
      updateTitle(project = project, titleInfoProviders = titleInfoProviders)
    }
  }

  internal fun updateTitle(project: Project?) {
    updateTitle(project = project, titleInfoProviders = getTitleInfoProviders())
  }

  private fun updateTitle(project: Project?, titleInfoProviders: List<TitleInfoProvider>) {
    if (!isUpdatingTitle.compareAndSet(false, true)) {
      return
    }

    try {
      if (AdvancedSettings.getBoolean("ide.show.fileType.icon.in.titleBar")) {
        // this property requires java.io.File
        frame.rootPane.putClientProperty("Window.documentFile", currentFile?.toFile())
      }

      val builder = StringBuilder()
      appendTitlePart(builder, title)
      appendTitlePart(builder, fileTitle)
      if (project != null) {
        for (extension in titleInfoProviders) {
          if (extension.isActive(project)) {
            val it = extension.getValue(project)
            if (!it.isEmpty()) {
              appendTitlePart(builder = builder, s = it, separator = " ")
            }
          }
        }
      }
      if (builder.isNotEmpty()) {
        frame.title = builder.toString()
      }
    }
    finally {
      isUpdatingTitle.set(false)
    }
  }

  fun updateView() {
    val rootPane = rootPane
    rootPane.updateToolbar()
    rootPane.updateMainMenuActions()
    rootPane.updateNorthComponents()
  }

  override fun getCurrentAccessibleContext(): AccessibleContext = frame.accessibleContext

  override fun getData(dataId: String): Any? {
    val project = project
    return when {
      CommonDataKeys.PROJECT.`is`(dataId) -> if (project != null && project.isInitialized) project else null
      IdeFrame.KEY.`is`(dataId) -> this
      PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS.`is`(dataId) -> {
        if (project == null || !project.isInitialized) {
          return null
        }

        val manager = project.serviceIfCreated<ToolWindowManager>() as? ToolWindowManagerImpl ?: return null
        manager.getLastActiveToolWindows().toList().toTypedArray()
      }
      PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.`is`(dataId) -> {
        if (project == null || !project.isInitialized) {
          null
        }
        else {
          (project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx)?.selectedEditor
        }
      }
      else -> null
    }
  }

  override fun getProject(): Project? = project

  // any activities that will not access a workspace model
  internal suspend fun setRawProject(project: Project) {
    if (this.project === project) {
      return
    }

    this.project = project

    withContext(Dispatchers.EDT) {
      applyInitBounds()
    }
    frameDecorator?.setProject()
  }

  internal suspend fun setProject(project: Project) {
    rootPane.setProject(project)
    activationTimestamp?.let {
      RecentProjectsManager.getInstance().setActivationTimestamp(project, it)
    }
  }

  @RequiresEdt
  internal fun setInitBounds(bounds: Rectangle?) {
    if (bounds != null && frame.isInFullScreen) {
      frame.rootPane.putClientProperty(INIT_BOUNDS_KEY, bounds)
    }
  }

  private fun applyInitBounds() {
    if (isInFullScreen) {
      val bounds = rootPane.getClientProperty(INIT_BOUNDS_KEY)
      rootPane.putClientProperty(INIT_BOUNDS_KEY, null)
      if (bounds is Rectangle) {
        ProjectFrameBounds.getInstance(project!!).markDirty(bounds)
        IDE_FRAME_EVENT_LOG.debug { "Applied init bounds for full screen from client property: $bounds" }
      }
    }
    else {
      ProjectFrameBounds.getInstance(project!!).markDirty(frame.bounds)
      IDE_FRAME_EVENT_LOG.debug { "Applied init bounds for non-fullscreen from the frame: ${frame.bounds}" }
    }
  }

  open suspend fun installDefaultProjectStatusBarWidgets(project: Project) {
    rootPane.statusBar!!.init(project, frame)
  }

  fun appClosing() {
    frameDecorator?.appClosing()
  }

  override fun dispose() {
    MouseGestureManager.getInstance().remove(this)
    balloonLayout?.let {
      balloonLayout = null
      (it as BalloonLayoutImpl).dispose()
    }

    frame.removeWindowListener(WindowCloseListener)

    // clear both our and swing hard refs
    if (ApplicationManager.getApplication().isUnitTestMode) {
      rootPane.removeNotify()
    }

    if (!WindowManagerEx.getInstanceEx().isFrameReused(this)) {
      frame.doDispose()
    }
  }

  override fun suggestChildFrameBounds(): Rectangle {
    val b = frame.bounds
    b.x += 100
    b.width -= 200
    b.y += 100
    b.height -= 200
    return b
  }

  override fun getBalloonLayout(): BalloonLayout? = balloonLayout

  override fun isInFullScreen(): Boolean = frameDecorator?.isInFullScreen ?: false

  override fun toggleFullScreen(state: Boolean): Job {
    val frameDecorator = frameDecorator
    if (frameDecorator == null || temporaryFixForIdea156004(state)) {
      return CompletableDeferred(value = Unit)
    }
    else {
      return frameDecorator.toggleFullScreen(state).asDeferred()
    }
  }

  fun storeStateForReuse() {
    frame.reusedFullScreenState = frameDecorator != null && frameDecorator.isInFullScreen
  }

  private fun getReusedFullScreenState(): Boolean {
    val reusedFullScreenState = frame.reusedFullScreenState
    frame.reusedFullScreenState = false
    return reusedFullScreenState
  }

  private fun temporaryFixForIdea156004(state: Boolean): Boolean {
    if (SystemInfoRt.isMac) {
      try {
        val modalBlockerField = Window::class.java.getDeclaredField("modalBlocker")
        modalBlockerField.isAccessible = true
        val modalBlocker = modalBlockerField.get(frame) as? Window
        if (modalBlocker != null) {
          ApplicationManager.getApplication().invokeLater({ toggleFullScreen(state) }, ModalityState.nonModal())
          return true
        }
      }
      catch (e: NoSuchFieldException) {
        LOG.error(e)
      }
      catch (e: IllegalAccessException) {
        LOG.error(e)
      }
    }
    return false
  }

  override fun notifyProjectActivation() {
    val currentTimeMillis = System.currentTimeMillis()
    activationTimestamp = currentTimeMillis
    project?.let {
      RecentProjectsManager.getInstance().setActivationTimestamp(project = it, timestamp = currentTimeMillis)
    }
  }

  internal val isTabbedWindow: Boolean
    get() = frameDecorator?.isTabbedWindow ?: false

  internal fun getDecorator(): IdeFrameDecorator? = frameDecorator

  open fun windowClosing(project: Project) {
    CloseProjectWindowHelper().windowClosing(project)
  }
}

private fun isTemporaryDisposed(frame: RootPaneContainer?): Boolean {
  return ClientProperty.isTrue(frame?.rootPane, ScreenUtil.DISPOSE_TEMPORARY)
}

// static object to ensure that we do not retain a project
private object WindowCloseListener : WindowAdapter() {
  override fun windowClosing(e: WindowEvent) {
    val frame = e.window as? IdeFrameImpl ?: return
    val frameHelper = frame.frameHelper?.helper as? ProjectFrameHelper ?: return
    val project = frameHelper.project ?: return
    if (isTemporaryDisposed(frame) || LaterInvocator.isInModalContext(frame, project)) {
      return
    }

    val app = ApplicationManager.getApplication()
    if (app != null && !app.isDisposed) {
      frameHelper.windowClosing(project)
    }
  }
}

private fun appendTitlePart(builder: StringBuilder, s: String?, separator: String) {
  if (!s.isNullOrBlank()) {
    if (builder.isNotEmpty()) {
      builder.append(separator)
    }
    builder.append(s)
  }
}