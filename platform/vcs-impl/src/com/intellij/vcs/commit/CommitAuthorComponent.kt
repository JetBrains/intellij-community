// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.util.text.StringUtil.unescapeXmlEntities
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.InplaceButton
import com.intellij.ui.awt.RelativePoint.getNorthWestOf
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.EventDispatcher
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.NamedColorUtil
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserEditor
import com.intellij.vcs.log.util.VcsUserUtil
import java.awt.event.ActionListener
import java.awt.event.KeyEvent.VK_ENTER
import java.util.*
import javax.swing.KeyStroke.getKeyStroke
import kotlin.properties.Delegates.observable

private val ENTER = getKeyStroke(VK_ENTER, 0)

class CommitAuthorComponent(private val project: Project) : NonOpaquePanel(), CommitAuthorTracker {
  private val userViewer = VcsUserViewer { user -> showEditorPopup(user) }
  private val dateViewer = VcsDateViewer { removeAuthorAndDate() }

  private var editorPopup: JBPopup? = null

  private val eventDispatcher = EventDispatcher.create(CommitAuthorListener::class.java)

  override var commitAuthor by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    userViewer.user = newValue
    dateViewer.hasAuthor = newValue != null

    updateVisibility()
    eventDispatcher.multicaster.commitAuthorChanged()
  }

  override var commitAuthorDate by observable<Date?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    dateViewer.date = newValue

    updateVisibility()
    eventDispatcher.multicaster.commitAuthorDateChanged()
  }

  init {
    layout = HorizontalLayout(JBUI.scale(4))
    add(userViewer)
    add(dateViewer)

    updateVisibility()
  }

  private fun updateVisibility() {
    userViewer.isVisible = userViewer.user != null
    dateViewer.isVisible = dateViewer.date != null
    isVisible = userViewer.isVisible || dateViewer.isVisible
  }

  override fun addCommitAuthorListener(listener: CommitAuthorListener, parent: Disposable) =
    eventDispatcher.addListener(listener, parent)

  private fun showEditorPopup(userToEdit: VcsUser?) {
    closeEditorPopup()
    editorPopup = createEditorPopup(userToEdit)
    editorPopup?.show(getNorthWestOf(userViewer))
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

  private fun removeAuthorAndDate() {
    commitAuthor = null
    commitAuthorDate = null
  }
}

private class VcsUserViewer(val clickListener: (VcsUser?) -> Unit) : NonOpaquePanel() {
  private val byLabel = JBLabel(message("label.by.author")).apply {
    foreground = NamedColorUtil.getInactiveTextColor()
    border = emptyRight(4)
  }

  private val link = object : LinkLabel<Any>(null, null) {
    override fun getStatusBarText(): String = unescapeXmlEntities(super.getStatusBarText())
  }

  var user by observable<VcsUser?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    link.text = newValue?.let { VcsUserUtil.getShortPresentation(it) }
    link.toolTipText = newValue?.let { escapeXmlEntities(it.toString()) }
  }

  init {
    link.setListener(LinkListener { _, _ -> clickListener(user) }, null)

    layout = HorizontalLayout(0)
    add(byLabel)
    add(link)
  }
}

private class VcsDateViewer(val deleteListener: () -> Unit) : NonOpaquePanel() {
  private val label = JBLabel().apply {
    foreground = NamedColorUtil.getInactiveTextColor()
  }
  private val closeButton = InplaceButton(IconButton(message("button.tooltip.remove.commit.author.date"),
                                                     AllIcons.Actions.Close,
                                                     AllIcons.Actions.CloseHovered)) {
    deleteListener()
  }

  var hasAuthor by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateText()
  }
  var date by observable<Date?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateText()
  }

  @Suppress("DialogTitleCapitalization")
  private fun updateText() {
    val newDate = date ?: return
    label.text = when {
      hasAuthor -> message("label.at.date.middle", DateFormatUtil.formatDate(newDate))
      else -> message("label.at.date.leading", DateFormatUtil.formatDate(newDate))
    }
    label.toolTipText = DateFormatUtil.formatDateTime(newDate)
  }

  init {
    layout = HorizontalLayout(0)
    add(label)
    add(closeButton)
  }
}