// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import sun.swing.SwingUtilities2
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JLabel


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

  private val label = object : JLabel(){
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
  }.apply {
    isEnabled = false
  }

  private var inited = false
  open fun getView(): JComponent {
    if(!inited) {
      init()
    }
    return this.label
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

  private fun init() {
    inited = true
  }

  private var disposable: Disposable? = null
  private var project: Project? = null

  private fun updateListeners() {
    if (added && project != null) {
      installListeners()
    } else {
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
      } else {
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
      val pnfm = fm

      val insets = label.getInsets(null)
      val width: Int = label.width - (insets.right + insets.left)

      val pnWidth = SwingUtilities2.stringWidth(label, pnfm, projectName)
      val textWidth = SwingUtilities2.stringWidth(label, fm, path)
      val symbolWidth = SwingUtilities2.stringWidth(label, fm, ellipsisSymbol)
      val delimiterWidth = SwingUtilities2.stringWidth(label, fm, delimiterSymbol)

      when {
        pnWidth > width -> {
          getView().toolTipText = path
          clippedProjectName = ""
          ""
        }

        pnWidth == width || pnWidth + symbolWidth + delimiterWidth >= width -> {
          label.toolTipText = path
          clippedProjectName = projectName
          ""
        }

        textWidth > width - pnWidth - delimiterWidth -> {
          getView().toolTipText = path
          clippedProjectName = projectName
          val clipString = clipString(label, path, width - pnWidth - delimiterWidth)
          if(clipString.isEmpty()) "" else "$delimiterSymbol$clipString"
        }
        else -> {
          getView().toolTipText = null
          clippedProjectName = projectName
          if(path.isEmpty())"" else "$delimiterSymbol$path"
        }
      }
    }
    label.text = clippedProjectName + clippedText
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

        return if(str.isEmpty()) "" else ellipsisSymbol + str
      }
    }
  }
}