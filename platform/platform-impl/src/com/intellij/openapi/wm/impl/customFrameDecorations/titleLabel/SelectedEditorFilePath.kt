// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.constraints.isDisposed
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import sun.swing.SwingUtilities2
import java.awt.*
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.UIManager


open class SelectedEditorFilePath(val disposable: Disposable) {
  companion object {
    const val fileSeparatorChar = '/'
    const val ellipsisSymbol = "\u2026"
  }

  private var clippedText: String? = null
    set(value) {
      if (value == field) return
      field = value
      label.revalidate()
      label.repaint()
    }

  fun isClipped(): Boolean {
    return clippedText.equals(path)
  }

  private val label = object : JLabel() {
    override fun paint(g: Graphics?) {
      g as Graphics2D

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setColor(UIManager.getColor("Label.disabledForeground"))
      val h = height - insets.top - insets.bottom

      SwingUtilities2.drawStringUnderlineCharAt(this, g, clippedText, -1, insets.left, getBaseline(width, h))
    }
  }.apply {
    isEnabled = false

  }

  var inited = false
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

  private var path: String? = null
    set(value) {
      if (value == field) return
      field = value
      label.text = path

      update()
    }

  private fun init() {
    inited = true
    var subscriptionDisposable: Disposable? = null

    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
          if (subscriptionDisposable != null && !subscriptionDisposable!!.isDisposed) {
            Disposer.dispose(subscriptionDisposable!!)
          }

          val dsp = Disposer.newDisposable()
          Disposer.register(disposable, dsp)
          subscriptionDisposable = dsp

          changeProject(project, dsp)
        }
      })

    getView().addComponentListener(resizedListener)
    Disposer.register(disposable, Disposable { getView().removeComponentListener(resizedListener) })
  }


  protected open fun changeProject(project: Project, dsp: Disposable) {
    val fileEditorManager = FileEditorManager.getInstance(project)

    fun updatePath() {
      path = if (fileEditorManager is FileEditorManagerEx) {
        fileEditorManager.currentFile?.canonicalPath
      }
      else {
        fileEditorManager?.selectedEditor?.file?.canonicalPath
      }
    }

    project.messageBus.connect(dsp).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
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

    update()
  }


  private fun update() {
    clippedText = path?.let {
      val fm = label.getFontMetrics(label.font)

      val insets = label.getInsets(null)
      val width: Int = label.width - (insets.right + insets.left)

      val textWidth = SwingUtilities2.stringWidth(label, fm, path)

      if (textWidth > width) {
        getView().toolTipText = path
        clipString(label, path!!, width)
      }
      else {
        getView().toolTipText = null
        path
      }
    }
  }

  private fun clipString(component: JComponent, string: String, maxWidth: Int): String {
    val fm = component.getFontMetrics(component.font)
    val symbolWidth = SwingUtilities2.stringWidth(component, fm, ellipsisSymbol)
    return when {
      symbolWidth > maxWidth -> ""
      symbolWidth == maxWidth -> ellipsisSymbol
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

        return ellipsisSymbol + str
      }
    }
  }
}