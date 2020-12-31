// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.util.text.StringUtil.unescapeXmlEntities
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.awt.RelativePoint.getNorthWestOf
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getInactiveTextColor
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserEditor
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import java.awt.event.ActionListener
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.KeyStroke.getKeyStroke
import kotlin.properties.Delegates.observable

private val ENTER = getKeyStroke(VK_ENTER, 0)

class CommitAuthorComponent(private val project: Project) : NonOpaquePanel(HorizontalLayout(0)), CommitAuthorTracker {
  private val viewer = VcsUserViewer().apply {
    setListener(LinkListener { _, _ -> showEditorPopup(user) }, null)
  }
  private var editorPopup: JBPopup? = null

  private val eventDispatcher = EventDispatcher.create(CommitAuthorListener::class.java)

  override var commitAuthor by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    isVisible = newValue != null
    viewer.user = newValue

    eventDispatcher.multicaster.commitAuthorChanged()
  }

  init {
    isVisible = false

    add(JBLabel(message("label.by.author")).apply {
      foreground = getInactiveTextColor()
      border = emptyRight(4)
    })
    add(viewer)
  }

  override fun addCommitAuthorListener(listener: CommitAuthorListener, parent: Disposable) =
    eventDispatcher.addListener(listener, parent)

  private fun showEditorPopup(userToEdit: VcsUser?) {
    closeEditorPopup()
    editorPopup = createEditorPopup(userToEdit)
    editorPopup?.show(getNorthWestOf(viewer))
  }

  private fun closeEditorPopup() {
    editorPopup?.cancel()
    editorPopup = null
  }

  private fun createEditorPopup(userToEdit: VcsUser?): JBPopup {
    val editor = VcsUserEditor(project).apply {
      setPreferredWidth(scale(500))

      user = userToEdit
      selectAll()
      setCaretPosition(0)
    }
    val applyCommitAuthor = ActionListener {
      commitAuthor = editor.user
      closeEditorPopup()
    }

    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(editor, editor)
      .setRequestFocus(true)
      .setCancelOnOtherWindowOpen(true)
      .setShowBorder(false)
      .setKeyboardActions(listOf(
        Pair(applyCommitAuthor, ENTER)
      ))
      .createPopup()
  }
}

private class VcsUserViewer : LinkLabel<Any>(null, null) {
  var user by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    text = newValue?.let { getShortPresentation(it) }
    toolTipText = newValue?.let { escapeXmlEntities(it.toString()) }
  }

  override fun getStatusBarText(): String = unescapeXmlEntities(super.getStatusBarText())
}