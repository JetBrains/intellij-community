// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
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
import sun.swing.SwingUtilities2
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.plaf.FontUIResource


open class SelectedEditorFilePath(val project: Project) {
  companion object {
    const val fileSeparatorChar = '\\'
    const val ellipsisSymbol = "\u2026"
    const val delimiterSymbol = " - "
  }

  private var clippedText: String? = null
  private var clippedProjectName: String = ""

  fun isClipped(): Boolean {
    return !clippedText.equals(path)
  }

  protected val projectLabel = object : JLabel() {
    override fun addNotify() {
      super.addNotify()
      installListeners()
    }

    override fun removeNotify() {
      super.removeNotify()
      unInstallListeners()
    }

    override fun setFont(font: Font) {
      super.setFont(fontUIResource(font))
    }
  }.apply {
    font = fontUIResource(font)
  }

  protected val classTitle = JLabel()
  private val productTitle = JLabel()
  private val productVersionTitle = JLabel()

  private fun fontUIResource(font: Font) = FontUIResource(font.deriveFont(font.style or Font.BOLD))

  private val pane = object : JPanel(MigLayout("ins 0, gap 0", "[min!][pref][pref][pref]push")) {
    override fun getMinimumSize(): Dimension {
      val minimumSize = super.getMinimumSize()
      if (shortProjectName.isEmpty()) return minimumSize

      val fm = projectLabel.getFontMetrics(projectLabel.font)
      val shortPnWidth = SwingUtilities2.stringWidth(projectLabel, fm, shortProjectName)

      return Dimension(shortPnWidth, minimumSize.height)
    }
  }.apply {
    add(projectLabel)
    add(classTitle, "growx")
    add(productTitle)
    add(productVersionTitle)
  }

  open fun getView(): JComponent {
    return pane
  }

  private val resizedListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      update()
    }
  }

  private var projectName: String = ""
  private var shortProjectName: String = ""

  private var shortPath: String = ""

  private var path: String = ""
    set(value) {
      if (value == field) return
      field = value
      shortPath = path.split(fileSeparatorChar).last()

      update()
    }

  private var disposable: Disposable? = null

  protected open fun installListeners() {
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

  private fun updatePath() {
    val fileEditorManager = FileEditorManager.getInstance(project)

    val file = if (fileEditorManager is FileEditorManagerEx) {
      val splittersFor = fileEditorManager.getSplittersFor(pane)
      splittersFor.currentFile
    }
    else {
      fileEditorManager?.selectedEditor?.file
    }

    path = file?.let { fl ->
      FrameTitleBuilder.getInstance().getFileTitle(project, fl)
    } ?: ""

    update()
  }

  protected fun updateProjectName() {
    shortProjectName = project.name
    projectName = FrameTitleBuilder.getInstance().getProjectTitle(project) ?: shortProjectName

    update()
  }

  private fun update() {
    val product = if (!SystemInfo.isMac && !SystemInfo.isGNOME) "${ApplicationNamesInfo.getInstance().fullProductName}${if (java.lang.Boolean.getBoolean(
        "ide.ui.version.in.title")) " ${ApplicationInfo.getInstance().fullVersion}"
    else ""}"
    else ""
    val fullToolTip = projectName + delimiterSymbol + path + product

    clippedText = path.let {
      val fm = classTitle.getFontMetrics(classTitle.font)
      val pnfm = projectLabel.getFontMetrics(projectLabel.font)

      val insets = classTitle.getInsets(null)
      val width: Int = getView().width - (insets.right + insets.left)

      var pnWidth = SwingUtilities2.stringWidth(projectLabel, pnfm, projectName)
      val shortPnWidth = SwingUtilities2.stringWidth(projectLabel, pnfm, shortProjectName)

      val classWidth = SwingUtilities2.stringWidth(classTitle, fm, path)
      val productWidth = SwingUtilities2.stringWidth(classTitle, fm, product)
      val shortClassWidth = SwingUtilities2.stringWidth(classTitle, fm, shortPath)

      val symbolWidth = SwingUtilities2.stringWidth(classTitle, fm, ellipsisSymbol)
      val delimiterWidth = SwingUtilities2.stringWidth(classTitle, fm, delimiterSymbol)

      if (pnWidth + classWidth + delimiterWidth + (if (path.isEmpty()) 0 else delimiterWidth) + productWidth < width) {
        projectLabel.toolTipText = null
        classTitle.toolTipText = null
        clippedProjectName = projectName

        return@let "${if (path.isEmpty()) "" else "$delimiterSymbol$path"}$delimiterSymbol$product"
      }

      val pn = if (pnWidth + classWidth + delimiterWidth > width) {
        pnWidth = shortPnWidth

        shortProjectName
      }
      else {
        projectName
      }

      when {
        pnWidth > width -> {
          projectLabel.toolTipText = fullToolTip
          classTitle.toolTipText = fullToolTip
          clippedProjectName = ""
          ""
        }

        pnWidth == width || pnWidth + symbolWidth + delimiterWidth >= width -> {
          projectLabel.toolTipText = fullToolTip
          classTitle.toolTipText = fullToolTip
          clippedProjectName = pn
          ""
        }

        classWidth > width - pnWidth - delimiterWidth -> {
          projectLabel.toolTipText = fullToolTip
          classTitle.toolTipText = fullToolTip
          clippedProjectName = pn
          val clipString = clipString(classTitle, path, width - pnWidth - delimiterWidth)
          if (clipString.isEmpty()) "" else "$delimiterSymbol$clipString"
        }
        else -> {
          projectLabel.toolTipText = if (pn == shortProjectName) projectName else null
          classTitle.toolTipText = null
          clippedProjectName = pn
          if (path.isEmpty()) "" else "$delimiterSymbol$path"
        }
      }
    }
    projectLabel.text = clippedProjectName
    classTitle.text = clippedText
  }

  private fun clipString(component: JComponent, string: String, maxWidth: Int): String {
    val fm = component.getFontMetrics(component.font)
    val symbolWidth = SwingUtilities2.stringWidth(component, fm, ellipsisSymbol)
    return when {
      symbolWidth >= maxWidth -> ""
      else -> {
        val availTextWidth = maxWidth - symbolWidth

        val separate = string.split(fileSeparatorChar)
        var str = ""
        var stringWidth = 0
        for (i in separate.lastIndex downTo 1) {
          stringWidth += SwingUtilities2.stringWidth(component, fm, separate[i] + fileSeparatorChar)
          if (stringWidth <= availTextWidth) {
            str = fileSeparatorChar + separate[i] + str
          }
        }

        return if (str.isEmpty()) "" else ellipsisSymbol + str
      }
    }
  }
}