// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.util.Alarm
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
import kotlin.math.min

open class SelectedEditorFilePath(private val onBoundsChanged: (() -> Unit)? = null ) {
  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClippingTitle()
  private val productTitle = DefaultPartTitle(" - ")
  private val productVersion = DefaultPartTitle(" ")
  private val superUserSuffix = DefaultPartTitle(" ")

  protected val components = listOf(projectTitle, classTitle, productTitle, productVersion, superUserSuffix)

  private val updater = Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication())
  private val UPDATER_TIMEOUT = 70

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      updateTitlePaths()
      update()
    }
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
    projectTitle.active = Registry.get("ide.borderless.title.project.path").asBoolean() || multipleSameNamed
    classTitle.active = Registry.get("ide.borderless.title.classpath").asBoolean() || classPathNeeded
    productTitle.active = Registry.get("ide.borderless.title.product").asBoolean()
    productVersion.active = Registry.get("ide.borderless.title.version").asBoolean()
  }

  open fun getView(): JComponent {
    return pane
  }

  private var disposable: Disposable? = null
  var project: Project? = null
    set(value) {
      if(field == value) return
      field = value

      installListeners()
    }

  var multipleSameNamed = false
    set(value) {
      if(field == value) return
      field = value

      updateTitlePaths()
      update()
    }


  var classPathNeeded = false
    set(value) {
      if(field == value) return
      field = value

      updateTitlePaths()
      update()
    }


  protected open fun installListeners() {
    project ?: return

    if (disposable != null) {
      unInstallListeners()
    }

    project?.let {
      val disp = Disposer.newDisposable()
      Disposer.register(it, disp)
      disposable = disp

      Registry.get("ide.borderless.title.project.path").addListener(registryListener, disp)
      Registry.get("ide.borderless.title.classpath").addListener(registryListener, disp)
      Registry.get("ide.borderless.title.product").addListener(registryListener, disp)
      Registry.get("ide.borderless.title.version").addListener(registryListener, disp)

      updateTitlePaths()

      it.messageBus.connect(disp).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          updatePath()
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          updatePath()
        }

        override fun selectionChanged(event: FileEditorManagerEvent) {
          updatePath()
        }
      })

      it.messageBus.connect(disp).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: MutableList<out VFileEvent>) {
            updatePath()
        }
      })
    }

    updateProjectName()
    updatePath()

    getView().addComponentListener(resizedListener)
  }

  protected open fun unInstallListeners() {
    disposable?.let {
      if (!Disposer.isDisposed(it))
        Disposer.dispose(it)
      disposable = null
    }

    pane.invalidate()

    getView().removeComponentListener(resizedListener)
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {

      updater.addRequest({
                           update()
                         }, UPDATER_TIMEOUT)
    }
  }

  private fun updatePath() {
    classTitle.longText = project?.let {
      val fileEditorManager = FileEditorManager.getInstance(it)

      val file = if (fileEditorManager is FileEditorManagerEx) {
        val splittersFor = fileEditorManager.getSplittersFor(getView())
        splittersFor.currentFile
      }
      else {
        fileEditorManager?.selectedEditor?.file
      }

      file?.let { fl ->
        FrameTitleBuilder.getInstance().getFileTitle(it, fl)
      } ?: ""
    } ?: ""

    update()
  }

  protected fun updateProjectName() {
    productTitle.longText = ApplicationNamesInfo.getInstance().fullProductName
    productVersion.longText = ApplicationInfo.getInstance().fullVersion ?: ""

    superUserSuffix.longText = ProjectFrameHelper.getSuperUserSuffix() ?: ""


    project?.let {
      val short = it.name
      val long = FrameTitleBuilder.getInstance().getProjectTitle(it) ?: short

      projectTitle.setProject(long, short)
      update()
    }
  }

  protected var isClipped = false
  var titleString = ""
  data class Pattern(val preferredWidth: Int, val createTitle: () -> String)

  private fun update() {
    updater.cancelAllRequests()

    val insets = getView().getInsets(null)
    val width: Int = getView().width - (insets.right + insets.left)

    val fm = label.getFontMetrics(label.font)

    components.forEach { it.refresh(label, fm) }

    isClipped = true

    val testSimple = testSimple(listOf<TitlePart>(productTitle, productVersion, superUserSuffix), width - (projectTitle.longWidth + classTitle.longWidth))


    val listOf = listOf(
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

    titleString = testSimple?.let {
      projectTitle.getLong() +
      classTitle.getLong() + it
    } ?: listOf.first { it.preferredWidth < width }.let {
        it.createTitle()
    }

    label.text = titleString
    label.toolTipText = if (!isClipped) null else components.joinToString(separator = "", transform = { it.toolTipPart })

    label.revalidate()
    label.repaint()

    onBoundsChanged?.invoke()
  }

  private fun testSimple(simplePaths: List<TitlePart>, simpleWidth: Int): String? {
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