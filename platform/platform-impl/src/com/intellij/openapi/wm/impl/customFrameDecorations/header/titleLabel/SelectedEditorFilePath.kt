// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.displayUrlRelativeToProject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.PlatformFrameTitleBuilder
import com.intellij.openapi.wm.impl.TitleInfoProvider.Companion.getProviders
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import sun.swing.SwingUtilities2
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent
import kotlin.math.min

internal open class SelectedEditorFilePath {
  var onBoundsChanged: (() -> Unit)? = null
  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClassTitlePane()

  private var simplePaths: List<TitlePart>? = null
  private var basePaths: List<TitlePart> = listOf(projectTitle, classTitle)
  protected var components = basePaths

  private val updater = Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication())
  private val UPDATER_TIMEOUT = 70

  protected open val captionInTitle: Boolean
    get() = true

  protected fun updateProjectPath() {
    updateTitlePaths()
    updateProject()
  }

  protected fun updatePaths() {
    updateTitlePaths()
    update()
  }

  protected val label = object : JLabel() {
    override fun getMinimumSize(): Dimension {
      return Dimension(projectTitle.shortWidth, super.getMinimumSize().height)
    }

    override fun getPreferredSize(): Dimension {
      val fm = getFontMetrics(font)
      val w = SwingUtilities2.stringWidth(this, fm, titleString) + JBUI.scale(5)
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
    projectTitle.active = instance.fullPathsInWindowHeader || multipleSameNamed
    classTitle.active = captionInTitle || classPathNeeded

    classTitle.fullPath = instance.fullPathsInWindowHeader || classPathNeeded
    updatePath()
  }

  open val view: JComponent
    get() {
      return pane
    }

  private var disposable: Disposable? = null
  var project: Project? = null
    set(value) {
      if (field == value) {
        return
      }
      field = value

      installListeners()
    }

  var multipleSameNamed = false
    set(value) {
      if (field == value) return
      field = value

      updateProjectPath()
    }


  var classPathNeeded = false
    set(value) {
      if (field == value) return
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

    val disp = Disposable {
      HelpTooltip.dispose(label)
      unInstallListeners()
    }

    Disposer.register(project, disp)
    disposable = disp

    val busConnection = project.messageBus.connect(disp)
    busConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      updateProjectPath()
    })

    simplePaths = getProviders().map { titleInfoProvider ->
      val partTitle = DefaultPartTitle(titleInfoProvider.borderlessPrefix, titleInfoProvider.borderlessSuffix)
      titleInfoProvider.addUpdateListener(project, disp) {
        partTitle.active = it.isActive(project)
        partTitle.longText = it.getValue(project)

        update()
      }
      partTitle
    }

    val shrinkingPaths: MutableList<TitlePart> = mutableListOf(projectTitle, classTitle)
    simplePaths?.let { shrinkingPaths.addAll(it) }
    components = shrinkingPaths
    updateTitlePaths()

    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        updatePathLater()
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        updatePathLater()
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        updatePathLater()
      }
    })

    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        updatePathLater()
      }
    })

    addAdditionalListeners(disp)

    updateProject()
    updatePath()

    view.addComponentListener(resizedListener)
    label.addAncestorListener(ancestorListener)
  }

  protected fun updatePathLater() {
    SwingUtilities.invokeLater {
      disposable?.let {
        updatePath()
      }
    }
  }

  protected open fun unInstallListeners() {
    disposable?.let {
      if (!Disposer.isDisposed(it))
        Disposer.dispose(it)
      disposable = null
    }

    pane.invalidate()

    view.removeComponentListener(resizedListener)
    label.removeAncestorListener(ancestorListener)
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {

      updater.addRequest({
                           update()
                         }, UPDATER_TIMEOUT)
    }
  }

  private val ancestorListener = object : AncestorListenerAdapter() {
    override fun ancestorMoved(event: AncestorEvent?) {
      HelpTooltip.hide(label)
    }
  }

  private fun updatePath() {
    val project = project
    if (project == null || project.isDisposed) {
      return
    }
    val fileEditorManager = FileEditorManager.getInstance(project)
    val file = (fileEditorManager as? FileEditorManagerEx)?.getSplittersFor(view)?.currentFile
               ?: fileEditorManager?.selectedEditor?.file
    if (file == null) {
      classTitle.classPath = ""
      classTitle.longText = ""
      update()
    }
    else {
      ReadAction.nonBlocking<Pair<String, String>> {
        val titleBuilder = FrameTitleBuilder.getInstance()
        val baseTitle = titleBuilder.getFileTitle(project, file)
        Pair(
          (titleBuilder as? PlatformFrameTitleBuilder)?.run {
            val fileTitle = VfsPresentationUtil.getPresentableNameForUI(project, file)
            if (!fileTitle.endsWith(file.presentableName) || file.parent == null) {
              fileTitle
            }
            else {
              displayUrlRelativeToProject(file, file.presentableUrl, project, true, false)
            }
          } ?: baseTitle, baseTitle)
      }
        .expireWith(project)
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.any()) {
          classTitle.classPath = it.first
          classTitle.longText = if (classTitle.fullPath) it.first else it.second
          update()
        }
        .submit(AppExecutorUtil.getAppExecutorService())
    }
  }

  private fun updateProject() {
    project?.let {
      projectTitle.project = it
      update()
    }
  }

  val toolTipNeeded: Boolean
    get() = basePaths.firstOrNull{!it.active} != null || isClipped

  open fun getCustomTitle(): String? = null

  protected var isClipped = false
  var titleString = ""

  data class Pattern(val preferredWidth: Int, val createTitle: () -> String)

  private fun update() {
    updater.cancelAllRequests()

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

    (if (isClipped || basePaths.firstOrNull{!it.active} != null) {
      components.filter { it.active || basePaths.contains(it) }.joinToString(separator = "", transform = { it.toolTipPart })
    } else null)?.let {
      HelpTooltip().setTitle(it).installOn(label)
    }

    label.revalidate()
    label.repaint()

    onBoundsChanged?.invoke()
  }

  private fun shrinkSimplePaths(simplePaths: List<TitlePart>, simpleWidth: Int): String? {
    isClipped = simplePaths.sumBy { it.longWidth } > simpleWidth

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