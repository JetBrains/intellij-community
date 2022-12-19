// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHCommentTextFieldModel
import org.jetbrains.plugins.github.pullrequest.comment.ui.submitAction
import java.awt.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.min

internal class GHEditableHtmlPaneHandle(private val project: Project,
                                        private val paneComponent: JComponent,
                                        private val getSourceText: () -> String,
                                        private val updateText: (String) -> CompletableFuture<out Any?>) {

  private val paneLayout = SingleSizeRestrictedComponentLayout()

  val panel = JPanel(null).apply {
    isOpaque = false
  }

  var maxPaneWidth: Int?
    get() = paneLayout.maxWidth
    set(value) {
      paneLayout.maxWidth = value
      panel.validate()
      panel.repaint()
    }
  var maxPaneHeight: Int?
    get() = paneLayout.maxHeight
    set(value) {
      paneLayout.maxHeight = value
      panel.validate()
      panel.repaint()
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

      val submitShortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommentInputComponentFactory.defaultSubmitShortcut)
      val newLineShortcutText = KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.ENTER)

      val cancelAction = swingAction(CommonBundle.getCancelButtonText()) {
        hideEditor()
      }

      val actions = CommentInputActionsComponentFactory.Config(
        primaryAction = MutableStateFlow(model.submitAction(GithubBundle.message("pull.request.comment.save"))),
        additionalActions = MutableStateFlow(listOf(cancelAction)),
        hintInfo = MutableStateFlow(CommentInputActionsComponentFactory.HintInfo(
          submitHint = GithubBundle.message("pull.request.comment.save.hint", submitShortcutText),
          newLineHint = GithubBundle.message("pull.request.new.line.hint", newLineShortcutText)
        ))
      )

      editor = GHCommentTextFieldFactory(model).create(GHCommentTextFieldFactory.ActionsConfig(actions, cancelAction))
      panel.remove(paneComponent)
      with(panel) {
        layout = BorderLayout()
        add(editor!!, BorderLayout.CENTER)
        validate()
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
      layout = paneLayout
      add(paneComponent)
      validate()
      repaint()
    }
    editor = null
  }

  private class SingleSizeRestrictedComponentLayout : LayoutManager2 {
    var maxWidth: Int? = null
    var maxHeight: Int? = null

    private var component: Component? = null

    override fun addLayoutComponent(comp: Component?, constraints: Any?) {
      component = comp
    }

    override fun addLayoutComponent(name: String?, comp: Component?) {
      component = comp
    }

    override fun removeLayoutComponent(comp: Component?) {
      if (comp == component) component = null
    }

    override fun minimumLayoutSize(parent: Container): Dimension =
      Dimension(0, 0)

    private fun getWidthRestriction(): Int = maxWidth?.let(JBUIScale::scale) ?: Int.MAX_VALUE
    private fun getHeightRestriction(): Int = maxHeight?.let(JBUIScale::scale) ?: Int.MAX_VALUE

    override fun preferredLayoutSize(parent: Container): Dimension {
      val prefSize = component?.preferredSize ?: return Dimension(0, 0)

      val prefWidth = min(prefSize.width, getWidthRestriction())
      val prefHeight = min(prefSize.height, getHeightRestriction())

      return Dimension(prefWidth, prefHeight).also {
        JBInsets.removeFrom(it, parent.insets)
      }
    }

    override fun maximumLayoutSize(target: Container): Dimension {
      val maxSize = component?.maximumSize ?: return Dimension(0, 0)

      val maxWidth = min(maxSize.width, getWidthRestriction())
      val maxHeight = min(maxSize.height, getHeightRestriction())

      return Dimension(maxWidth, maxHeight).also {
        JBInsets.removeFrom(it, target.insets)
      }
    }

    override fun layoutContainer(parent: Container) {
      val bounds = Rectangle(0, 0, parent.width, parent.height)
      JBInsets.removeFrom(bounds, parent.insets)

      bounds.width = min(getWidthRestriction(), bounds.width)
      bounds.height = min(getHeightRestriction(), bounds.height)
      component?.bounds = bounds
    }

    override fun getLayoutAlignmentX(target: Container?): Float = 0f
    override fun getLayoutAlignmentY(target: Container?): Float = 0f
    override fun invalidateLayout(target: Container?) = Unit
  }
}