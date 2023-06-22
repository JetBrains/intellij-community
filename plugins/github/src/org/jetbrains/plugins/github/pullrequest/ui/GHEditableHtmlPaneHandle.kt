// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.submitAction
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHEditableHtmlPaneHandle(private val project: Project,
                                        private val paneComponent: JComponent,
                                        maxEditorWidth: Int? = null,
                                        private val getSourceText: () -> String,
                                        private val updateText: (String) -> CompletableFuture<out Any?>) {

  constructor(project: Project, paneComponent: JComponent, getSourceText: () -> String, updateText: (String) -> CompletableFuture<out Any?>)
    : this(project, paneComponent, null, getSourceText, updateText)

  private val editorPaneLayout = SizeRestrictedSingleComponentLayout.constant(maxWidth = maxEditorWidth)

  val panel = JPanel(null).apply {
    isOpaque = false
  }

  private var editor: JComponent? = null

  init {
    hideEditor()
  }

  fun showAndFocusEditor() {
    if (editor == null) {
      val model = GHCommentTextFieldModel(project, getSourceText()) { newText ->
        updateText(newText).successOnEdt {
          hideEditor()
        }
      }

      val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText

      val cancelAction = swingAction(CommonBundle.getCancelButtonText()) {
        hideEditor()
      }

      val actions = CommentInputActionsComponentFactory.Config(
        primaryAction = MutableStateFlow(model.submitAction(GithubBundle.message("pull.request.comment.save"))),
        cancelAction = MutableStateFlow(cancelAction),
        submitHint = MutableStateFlow(GithubBundle.message("pull.request.comment.save.hint", submitShortcutText))
      )

      editor = GHCommentTextFieldFactory(model).create(actions)
      panel.remove(paneComponent)
      with(panel) {
        layout = editorPaneLayout
        add(editor!!)
        revalidate()
        repaint()
      }
    }

    editor?.let {
      CollaborationToolsUIUtil.focusPanel(it)
    }
  }

  private fun hideEditor() {
    editor?.let {
      panel.remove(it)
    }
    with(panel) {
      layout = BorderLayout()
      add(paneComponent, BorderLayout.CENTER)
      revalidate()
      repaint()
    }
    editor = null
  }
}