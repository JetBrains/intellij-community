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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.IdeFrameImpl
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel


open class SelectedEditorFilePath(private val onBoundsChanged: (() -> Unit)? = null ) {
  companion object{
    private val PROJECT_PATH_REGISTRY = Registry.get("ide.borderless.title.project.path")
    private val CLASSPATH_REGISTRY = Registry.get("ide.borderless.title.classpath")
    private val PRODUCT_REGISTRY = Registry.get("ide.borderless.title.product")
    private val VERSION_REGISTRY = Registry.get("ide.borderless.title.version")
    private val SUPERUSER_REGISTRY = Registry.get("ide.borderless.title.superuser")
  }

  private val LOGGER = logger<SelectedEditorFilePath>()

  private val projectTitle = ProjectTitlePane()
  private val classTitle = ClippingTitle()
  private val productTitle = DefaultPartTitle(" - ")
  private val productVersion = DefaultPartTitle(" ")
  private val superUserSuffix = DefaultPartTitle(" ")

  protected val components = listOf(projectTitle, classTitle, productTitle, productVersion, superUserSuffix)

  private val registryListener = object : RegistryValueListener.Adapter() {
    override fun afterValueChanged(value: RegistryValue) {
      updateTitlePaths()
      update()
    }
  }

  private val pane = object : JPanel(MigLayout("ins 0, gap 0", "[min!][pref][pref][pref][pref]push")){
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
    
    add(projectTitle.component)
    add(classTitle.component, "growx")
    add(productTitle.component)
    add(productVersion.component)
    add(superUserSuffix.component)
  }

  private fun updateTitlePaths() {
    projectTitle.active = PROJECT_PATH_REGISTRY.asBoolean()
    classTitle.active = CLASSPATH_REGISTRY.asBoolean()
    productTitle.active = PRODUCT_REGISTRY.asBoolean()
    productVersion.active = VERSION_REGISTRY.asBoolean()
    superUserSuffix.active = SUPERUSER_REGISTRY.asBoolean()
  }

  open fun getView(): JComponent {
    return pane
  }

  private var disposable: Disposable? = null
  private var project: Project? = null
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
      SUPERUSER_REGISTRY.addListener(registryListener, disp)

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

    installListeners()
  }

  protected fun updateProjectName() {
    productTitle.longText = ApplicationNamesInfo.getInstance().fullProductName
    productVersion.longText = ApplicationInfo.getInstance().fullVersion ?: ""

    superUserSuffix.longText = IdeFrameImpl.getSuperUserSuffix() ?: ""


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
      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + superUserSuffix.longWidth + productVersion.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.showLong")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showLong()
        superUserSuffix.showLong()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth + productVersion.longWidth + superUserSuffix.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, superUserSuffix.SHOW_SHORT")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showLong()
        superUserSuffix.showShort()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productVersion.longWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, superUserSuffix.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showLong()
        superUserSuffix.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productVersion.shortWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.SHOW_SHORT")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        productVersion.showShort()
        superUserSuffix.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.showLong, productVersion.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showLong()
        superUserSuffix.hide()
        productVersion.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth + productTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.SHOW_SHORT")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.showShort()
        productVersion.hide()
        superUserSuffix.hide()
      }

      width > projectTitle.longWidth + classTitle.longWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.showLong, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showLong()
        classTitle.showLong()
        productTitle.hide()
        productVersion.hide()
        superUserSuffix.hide()
      }

      width > projectTitle.longWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHRINK: ${width - projectTitle.longWidth}, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showLong()
        productTitle.hide()
        productVersion.hide()
        superUserSuffix.hide()
        classTitle.shrink(width - projectTitle.longWidth)
      }

      width > projectTitle.shortWidth + classTitle.shortWidth -> {
        //LOGGER.info("projectTitle.showLong, classTitle.SHOW_SHORT, productTitle.HIDE, productVersion.HIDE")

        projectTitle.shrink(width - classTitle.shortWidth)
        productTitle.hide()
        productVersion.hide()
        superUserSuffix.hide()
        classTitle.showShort()
      }

      width > projectTitle.shortWidth -> {
        //LOGGER.info("projectTitle.SHOW_SHORT, classTitle.HIDE, productTitle.HIDE, productVersion.HIDE")

        projectTitle.showShort()
        productTitle.hide()
        productVersion.hide()
        superUserSuffix.hide()
        classTitle.hide()
      }
    }

    val clipped = isClipped()
    components.forEach {
      it.setToolTip(if (!clipped) null else "${projectTitle.toolTipPart}${classTitle.toolTipPart}${productTitle.toolTipPart}${productVersion.toolTipPart}") }

    onBoundsChanged?.invoke()
  }
}