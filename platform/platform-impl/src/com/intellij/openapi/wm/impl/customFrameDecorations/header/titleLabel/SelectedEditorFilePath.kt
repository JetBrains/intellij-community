// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import net.miginfocom.swing.MigLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel


open class SelectedEditorFilePath {
  private val LOGGER = logger<SelectedEditorFilePath>()

  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClippingTitle()
  private val productTitle = DefaultPartTitle()
  private val productVersion = DefaultPartTitle()

  protected val components = listOf(projectTitle, classTitle, productTitle, productVersion)

  private val pane = object : JPanel(MigLayout("ins 0, gap 0", "[min!][pref][pref][pref]push")){
    override fun addNotify() {
      super.addNotify()
      installListeners()
    }

    override fun removeNotify() {
      super.removeNotify()
      unInstallListeners()
    }
  }.apply {
    add(projectTitle.component)
    add(classTitle.component, "growx")
    add(productTitle.component)
    add(productVersion.component)
  }

  open fun getView(): JComponent {
    return pane
  }

  private var disposable: Disposable? = null
  private var project: Project? = null
  protected open fun installListeners() {
    project ?: return

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

    getView().removeComponentListener(resizedListener)
  }

  fun isClipped(): Boolean {
    for (component in components) {
      if(component.isClipped) return true
    }
    return false
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      update()
    }
  }

  private fun updatePath() {
    classTitle.longText = project?.let {
      val fileEditorManager = FileEditorManager.getInstance(it)

      val file = if (fileEditorManager is FileEditorManagerEx) {
        val splittersFor = fileEditorManager.getSplittersFor(pane)
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

  fun setProject(project: Project) {
    this.project = project

    if (disposable != null) {
      unInstallListeners()
    }

    val disp = Disposer.newDisposable()
    Disposer.register(project, disp)
    disposable = disp

    project.messageBus.connect(disp).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
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

    installListeners()
  }

  protected fun updateProjectName() {
    if (!SystemInfo.isMac && !SystemInfo.isGNOME) productTitle.longText = ApplicationNamesInfo.getInstance().fullProductName else productTitle.ignore()

    if(java.lang.Boolean.getBoolean("ide.ui.version.in.title")) productVersion.longText = ApplicationInfo.getInstance().fullVersion else productVersion.ignore()


    project?.let {
      val short = it.name
      val long = FrameTitleBuilder.getInstance().getProjectTitle(it) ?: short

      projectTitle.setProject(long, short)
      update()
    }
  }

  private fun update() {
    val insets = getView().getInsets(null)
    val width: Int = getView().width - (insets.right + insets.left)

    components.forEach{it.refresh()}

    when {
      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + productVersion.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.showLong")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showLong()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + productVersion.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.SHOW_SHORT")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showShort()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.SHOW_SHORT, productVersion.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showShort()
        productVersion.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.hide()
        productVersion.hide()
      }

      width > projectTitle.longWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHRINK: ${width - projectTitle.longWidth}, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showLong()
        productTitle.hide()
        productVersion.hide()
        classTitle.shrink(width - projectTitle.longWidth)
      }

      width > projectTitle.shortWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHOW_SHORT, productTitle.HIDE, productVersion.HIDE")

        projectTitle.shrink(width - classTitle.shortWidth)
        productTitle.hide()
        productVersion.hide()
        classTitle.showShort()
      }

      width > projectTitle.shortWidth -> {
        //LOGGER.info("projectTitle.SHOW_SHORT, classTitle.HIDE, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showShort()
        productTitle.hide()
        productVersion.hide()
        classTitle.hide()
      }
    }

    components.forEach { it.component.toolTipText = if (!isClipped()) null else "${projectTitle.toolTip}${classTitle.toolTip}${productTitle.toolTip}${productVersion.toolTip}" }

  }
}