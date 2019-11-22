// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import sun.swing.SwingUtilities2
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.min


open class SelectedEditorFilePath(private val onBoundsChanged: (() -> Unit)? = null ) {
  companion object{
    private val PROJECT_PATH_REGISTRY = Registry.get("ide.borderless.title.project.path")
    private val CLASSPATH_REGISTRY = Registry.get("ide.borderless.title.classpath")
    private val PRODUCT_REGISTRY = Registry.get("ide.borderless.title.product")
    private val VERSION_REGISTRY = Registry.get("ide.borderless.title.version")
  }

  private val LOGGER = logger<SelectedEditorFilePath>()

  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClippingTitle()
  private val productTitle = DefaultPartTitle(" - ")
  private val productVersion = DefaultPartTitle(" ")
  private val superUserSuffix = DefaultPartTitle(" ")

  protected val components = listOf(projectTitle, classTitle, productTitle, productVersion, superUserSuffix)

  private val updater = Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication())
  private val UPDATER_TIMEOUT = 70

  private val registryListener = object : RegistryValueListener.Adapter() {
    override fun afterValueChanged(value: RegistryValue) {
      updateTitlePaths()
      update()
    }
  }

  protected val label = object : JComponent() {
    override fun getMinimumSize(): Dimension {
      return Dimension(projectTitle.shortWidth, super.getMinimumSize().height)
    }

    override fun getPreferredSize(): Dimension {
      val fm = getFontMetrics(font)
      val w = SwingUtilities2.stringWidth(this, fm, titleString) + JBUI.scale(5)
      return Dimension(min(parent.width, w), fm.height)
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

  }.apply {
    isOpaque = false
    add(label)
  }

  private fun updateTitlePaths() {
    projectTitle.active = PROJECT_PATH_REGISTRY.asBoolean() || multipleSameNamed
    classTitle.active = CLASSPATH_REGISTRY.asBoolean() || classPathNeeded
    productTitle.active = PRODUCT_REGISTRY.asBoolean()
    productVersion.active = VERSION_REGISTRY.asBoolean()
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

      PROJECT_PATH_REGISTRY.addListener(registryListener, disp)
      CLASSPATH_REGISTRY.addListener(registryListener, disp)
      PRODUCT_REGISTRY.addListener(registryListener, disp)
      VERSION_REGISTRY.addListener(registryListener, disp)

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

  private fun update() {
    updater.cancelAllRequests()

    val insets = getView().getInsets(null)
    val width: Int = getView().width - (insets.right + insets.left)

    val fm = label.getFontMetrics(label.font)

    components.forEach{it.refresh(label, fm)}

    isClipped = true

    titleString = when {
      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + superUserSuffix.longWidth + productVersion.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.showLong")

        isClipped = components.any{!it.active}

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getLong()+
        productVersion.getLong()+
        superUserSuffix.getLong()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + productVersion.longWidth + superUserSuffix.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, superUserSuffix.SHOW_SHORT")

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getLong()+
        productVersion.getLong()+
        superUserSuffix.getShort()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productVersion.longWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, superUserSuffix.HIDE")

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getLong()+
        productVersion.getLong()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productVersion.shortWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.SHOW_SHORT")

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getLong()+
        productVersion.getShort()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.HIDE")

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getLong()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.SHOW_SHORT")

        projectTitle.getLong()+
        classTitle.getLong()+
        productTitle.getShort()
      }

      width > projectTitle.longWidth + classTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.HIDE, productVersion.HIDE")

        projectTitle.getLong()+
        classTitle.getLong()
      }

      width > projectTitle.longWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHRINK: ${width - projectTitle.longWidth}, productTitle.HIDE, productVersion.HIDE")

        projectTitle.getLong()+
        classTitle.shrink(label, fm,width - projectTitle.longWidth)
      }

      width > projectTitle.shortWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHOW_SHORT, productTitle.HIDE, productVersion.HIDE")

        projectTitle.shrink(label, fm,width - classTitle.shortWidth)+
        classTitle.getShort()
      }

      else -> {
        //LOGGER.info("projectTitle.SHOW_SHORT, classTitle.HIDE, productTitle.HIDE, productVersion.HIDE")
        projectTitle.getShort()
      }
    }

    label.toolTipText = if(!isClipped) null else components.joinToString(separator = "", transform = {it.toolTipPart})

    label.revalidate()
    label.repaint()

    onBoundsChanged?.invoke()
  }
}