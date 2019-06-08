// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import net.miginfocom.swing.MigLayout
import sun.swing.SwingUtilities2
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.plaf.FontUIResource


open class SelectedEditorFilePath() {
  companion object {
    const val fileSeparatorChar = '/'
    const val ellipsisSymbol = "\u2026"
    const val delimiterSymbol = " - "
  }

  private var clippedText: String? = null
  private var clippedProjectName: String = ""

  fun isClipped(): Boolean {
    return clippedText.equals(path)
  }

  private var added = false

  protected val projectLabel = object : JLabel(){
    override fun addNotify() {
      super.addNotify()
      added = true
      updateListeners()
    }

    override fun removeNotify() {
      super.removeNotify()
      added = false
      updateListeners()
    }

    override fun setFont(font: Font) {
      super.setFont(fontUIResource(font))
    }
  }.apply {
    font = fontUIResource(font)
  }

  private fun fontUIResource(font: Font) = FontUIResource(font.deriveFont(font.style or Font.BOLD))


  protected val label = JLabel()

  private val pane = JPanel(MigLayout("ins 0, gap 0", "[min!][pref]push")).apply {
    add(projectLabel)
    add(label, "growx")
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

  private var path: String = ""
    set(value) {
      if (value == field) return
      field = value
      update()
    }

  private var disposable: Disposable? = null
  private var project: Project? = null

  private fun updateListeners() {
    if (added && project != null) {
      installListeners()
    }
    else {
      unInstallListeners()
    }
  }

  private fun installListeners() {
    if (disposable != null) {
      unInstallListeners()
    }

    project?.let {
      val disp = Disposer.newDisposable()
      Disposer.register(it, disp)
      disposable = disp

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

      updatePath()
    }

    getView().addComponentListener(resizedListener)
  }

  private fun unInstallListeners() {
    disposable?.let {
      if (!Disposer.isDisposed(it))
        Disposer.dispose(it)
      disposable = null
    }

    getView().removeComponentListener(resizedListener)
  }

  private fun updatePath() {
    path = ""
    project?.let {
      val fileEditorManager = FileEditorManager.getInstance(it)

      path = if (fileEditorManager is FileEditorManagerEx) {
        fileEditorManager.currentFile?.canonicalPath ?: ""
      }
      else {
        fileEditorManager?.selectedEditor?.file?.canonicalPath ?: ""
      }
    }
    update()
  }

  fun setProject(project: Project) {
    projectName = project.name
    this.project = project
    updateListeners()
  }

  private fun update() {
    clippedText = path.let {
      val fm = label.getFontMetrics(label.font)
      val pnfm = projectLabel.getFontMetrics(projectLabel.font)

      val insets = label.getInsets(null)
      val width: Int = getView().width - (insets.right + insets.left)

      val pnWidth = SwingUtilities2.stringWidth(projectLabel, pnfm, projectName)
      val textWidth = SwingUtilities2.stringWidth(label, fm, path)
      val symbolWidth = SwingUtilities2.stringWidth(label, fm, ellipsisSymbol)
      val delimiterWidth = SwingUtilities2.stringWidth(label, fm, delimiterSymbol)

      when {
        pnWidth > width -> {
          projectLabel.toolTipText = path
          label.toolTipText = path
          clippedProjectName = ""
          ""
        }

        pnWidth == width || pnWidth + symbolWidth + delimiterWidth >= width -> {
          projectLabel.toolTipText = path
          label.toolTipText = path
          clippedProjectName = projectName
          ""
        }

        textWidth > width - pnWidth - delimiterWidth -> {
          projectLabel.toolTipText = path
          label.toolTipText = path
          clippedProjectName = projectName
          val clipString = clipString(label, path, width - pnWidth - delimiterWidth)
          if (clipString.isEmpty()) "" else "$delimiterSymbol$clipString"
        }
        else -> {
          projectLabel.toolTipText = null
          label.toolTipText = null
          clippedProjectName = projectName
          if (path.isEmpty()) "" else "$delimiterSymbol$path"
        }
      }
    }
    projectLabel.text = clippedProjectName
    label.text = clippedText
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