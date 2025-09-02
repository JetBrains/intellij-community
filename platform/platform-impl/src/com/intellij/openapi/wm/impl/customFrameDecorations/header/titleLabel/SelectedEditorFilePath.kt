// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.displayUrlRelativeToProject
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.PlatformFrameTitleBuilder
import com.intellij.openapi.wm.impl.TitleInfoProvider.Companion.getProviders
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal open class SelectedEditorFilePath(frame: JFrame) {
  var onBoundsChanged: (() -> Unit)? = null
  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClassTitlePane()

  private var simplePaths: List<TitlePart>? = null
  private var basePaths = listOf<TitlePart>(projectTitle, classTitle)
  protected var components = basePaths

  private val updatePathRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val updateViewRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  protected open val captionInTitle: Boolean
    get() = true

  init {
    val scope = service<CoreUiCoroutineScopeHolder>().coroutineScope
    val updatePathJob = scope.launch {
      updatePathRequests
        .debounce(100.milliseconds)
        .collectLatest {
          updatePath()
        }
    }
    val updateJob = scope.launch {
      updateViewRequests
        .debounce(70.milliseconds)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            update()
          }
        }
    }
    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(p0: WindowEvent?) {
        updatePathJob.cancel()
        updateJob.cancel()
      }
    })
  }

  protected fun updateProjectPath() {
    updateTitlePaths()
    updateProject()
  }

  private fun updatePaths() {
    updateTitlePaths()
    scheduleViewUpdate()
  }

  protected val label = object : JLabel() {
    override fun getMinimumSize(): Dimension {
      return Dimension(projectTitle.shortWidth, super.getMinimumSize().height)
    }

    override fun getPreferredSize(): Dimension {
      val fm = getFontMetrics(font)
      val w = UIUtil.computeStringWidth(this, fm, titleString) + JBUI.scale(5)
      return Dimension(min(parent.width, w), super.getPreferredSize().height)
    }

    override fun paintComponent(g: Graphics) {
      val fm = getFontMetrics(font)
      g as Graphics2D
      UISettings.setupAntialiasing(g)
      g.drawString(titleString, 0, fm.ascent)
    }
  }

  @Suppress("SpellCheckingInspection")
  private var pane = object : JPanel(MigLayout("ins 0, novisualpadding, gap 0", "[]push")) {
    override fun addNotify() {
      super.addNotify()
      installListeners()
    }

    override fun removeNotify() {
      super.removeNotify()
      unInstallListeners()
    }

    override fun getMinimumSize(): Dimension {
      return Dimension(projectTitle.shortWidth, super.getMinimumSize().height)
    }

    override fun setForeground(fg: Color?) {
      super.setForeground(fg)
      label.foreground = fg
    }
  }.apply {
    isOpaque = false
    add(label)
  }

  private fun updateTitlePaths() {
    val uiSettings = UISettings.getInstance()
    projectTitle.active = uiSettings.fullPathsInWindowHeader || multipleSameNamed
    classTitle.active = captionInTitle || classPathNeeded

    classTitle.fullPath = uiSettings.fullPathsInWindowHeader || classPathNeeded
    schedulePathUpdate()
  }

  open val view: JComponent
    get() = pane

  private var disposable: CheckedDisposable? = null
  var project: Project? = null
    set(value) {
      if (field === value) {
        return
      }

      field = value
      installListeners()
    }

  var multipleSameNamed: Boolean = false
    set(value) {
      if (field == value) {
        return
      }

      field = value
      updateProjectPath()
    }

  var classPathNeeded: Boolean = false
    set(value) {
      if (field == value) {
        return
      }

      field = value
      updatePaths()
    }

  protected open fun addAdditionalListeners(disp: Disposable) {
  }

  protected open fun installListeners() {
    val project = project ?: return

    if (disposable != null) {
      unInstallListeners()
    }

    val disposable = Disposer.newCheckedDisposable()
    Disposer.register(project, disposable)
    Disposer.register(disposable) {
      HelpTooltip.dispose(label)
      unInstallListeners()
    }
    this.disposable = disposable

    val busConnection = project.messageBus.connect(disposable)
    busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      updateProjectPath()
    })

    simplePaths = getProviders().map { titleInfoProvider ->
      val partTitle = DefaultPartTitle(titleInfoProvider.borderlessPrefix, titleInfoProvider.borderlessSuffix)
      titleInfoProvider.addUpdateListener(project, disposable) {
        partTitle.active = it.isActive(project)
        partTitle.longText = it.getValue(project)

        scheduleViewUpdate()
      }
      partTitle
    }

    val shrinkingPaths = mutableListOf<TitlePart>(projectTitle, classTitle)
    simplePaths?.let(shrinkingPaths::addAll)
    components = shrinkingPaths
    updateTitlePaths()

    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        schedulePathUpdate()
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        schedulePathUpdate()
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        schedulePathUpdate()
      }
    })

    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        schedulePathUpdate()
      }
    })

    addAdditionalListeners(disposable)

    updateProject()
    schedulePathUpdate()

    view.addComponentListener(resizedListener)
    label.addAncestorListener(ancestorListener)
  }

  protected fun schedulePathUpdate() {
    check(updatePathRequests.tryEmit(Unit))
  }

  protected open fun unInstallListeners() {
    disposable?.let {
      if (!it.isDisposed) {
        Disposer.dispose(it)
      }
      disposable = null
    }

    pane.invalidate()

    view.removeComponentListener(resizedListener)
    label.removeAncestorListener(ancestorListener)
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      scheduleViewUpdate()
    }
  }

  private val ancestorListener = object : AncestorListenerAdapter() {
    override fun ancestorMoved(event: AncestorEvent?) {
      HelpTooltip.hide(label)
    }
  }

  private suspend fun updatePath() {
    val project = project
    if (project == null || project.isDisposed) {
      return
    }

    val fileEditorManager = FileEditorManager.getInstance(project)
    val file = withContext(Dispatchers.EDT) {
      val file = (fileEditorManager as? FileEditorManagerEx)?.getSplittersFor(view)?.currentFile ?: fileEditorManager?.selectedEditor?.file
      if (file == null) {
        classTitle.classPath = ""
        classTitle.longText = ""
        scheduleViewUpdate()
      }
      file
    } ?: return

    val titleBuilder = serviceAsync<FrameTitleBuilder>()
    val baseTitle = titleBuilder.getFileTitleAsync(project, file)
    val result = readAction {
      val first = (titleBuilder as? PlatformFrameTitleBuilder)?.run {
        val fileTitle = VfsPresentationUtil.getPresentableNameForUI(project, file)
        if (!fileTitle.endsWith(file.presentableName) || file.parent == null) {
          fileTitle
        }
        else {
          displayUrlRelativeToProject(
            file = file,
            url = file.presentableUrl,
            project = project,
            isIncludeFilePath = true,
            moduleOnTheLeft = false,
          )
        }
      } ?: baseTitle
      Pair(first, baseTitle)
    }
    withContext(Dispatchers.EDT) {
      classTitle.classPath = result.first
      classTitle.longText = if (classTitle.fullPath) result.first else result.second
      scheduleViewUpdate()
    }
  }

  private fun scheduleViewUpdate() {
    check(updateViewRequests.tryEmit(Unit))
  }

  private fun updateProject() {
    project?.let {
      projectTitle.project = it
      scheduleViewUpdate()
    }
  }

  val toolTipNeeded: Boolean
    get() = basePaths.firstOrNull{!it.active} != null || isClipped

  open fun getCustomTitle(): String? = null

  private var isClipped = false
  var titleString: String = ""

  data class Pattern(val preferredWidth: Int, val createTitle: () -> String)

  private suspend fun update() {
    val insets = view.getInsets(null)
    val width: Int = view.width - (insets.right + insets.left)

    val fm = label.getFontMetrics(label.font)

    components.forEach { it.refresh(label, fm) }

    isClipped = true

    val shrankSimplePaths = simplePaths?.let { shrinkSimplePaths(it, width - (projectTitle.longWidth + classTitle.longWidth)) }

    val pathPatterns = listOf(
      Pattern(projectTitle.longWidth + classTitle.shortWidth) {
        projectTitle.getLong() +
        classTitle.shrink(label, fm, width - projectTitle.longWidth)
      },
      Pattern(projectTitle.shortWidth + classTitle.shortWidth) {
        projectTitle.shrink(label, fm, width - classTitle.shortWidth) +
        classTitle.getShort()
      },
      Pattern(0) {
        projectTitle.getShort()
      })

    titleString = shrankSimplePaths?.let {
      projectTitle.getLong() +
      classTitle.getLong() + it
    } ?: pathPatterns.firstOrNull { it.preferredWidth < width }?.let { it.createTitle() } ?: ""

    getCustomTitle()?.let {
      titleString = it
    }
    label.text = titleString
    HelpTooltip.dispose(label)

    (if (isClipped || basePaths.firstOrNull { !it.active } != null) {
      components.filter { it.active || basePaths.contains(it) }.joinToString(separator = "", transform = { it.toolTipPart })
    }
    else null)?.let {
      HelpTooltip().setTitle(it).installOn(label)
      CustomHeader.ensureClickTransparent(label)
    }

    coroutineContext.ensureActive()

    label.revalidate()
    label.repaint()

    onBoundsChanged?.invoke()
  }

  private fun shrinkSimplePaths(simplePaths: List<TitlePart>, simpleWidth: Int): String? {
    isClipped = simplePaths.sumOf { it.longWidth } > simpleWidth

    for (i in simplePaths.size - 1 downTo 0) {
      var beforeWidth = 0
      var beforeString = ""

      for (j in 0 until i) {
        val titlePart = simplePaths[j]
        beforeWidth += titlePart.longWidth
        beforeString += titlePart.getLong()
      }

      val testWidth = simpleWidth - beforeWidth
      val path = simplePaths[i]

      if (testWidth < 0) continue

      return when {
        testWidth > path.longWidth -> beforeString + path.getLong()
        testWidth > path.shortWidth -> beforeString + path.getShort()
        else -> beforeString
      }
    }

    return null
  }
}