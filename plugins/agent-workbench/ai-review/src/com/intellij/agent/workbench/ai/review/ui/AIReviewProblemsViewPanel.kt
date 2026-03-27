// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.model.AIReviewSession
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel.State
import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

internal class AIReviewProblemsViewPanel(
  project: Project,
  cs: CoroutineScope,
  id: String,
  state: ProblemsViewState,
  name: @Nls String,
  internal val session: AIReviewSession,
) : ProblemsViewPanel(project, id, state, { name }) {

  companion object {
    private const val TOOLBAR_GROUP_ID: String = "AIReview.ToolWindow.Toolbar"
    private const val POPUP_GROUP_ID: String = "AIReview.ToolWindow.TreePopup"

    internal val PANEL_KEY: DataKey<AIReviewProblemsViewPanel> = DataKey.create("AIReview.ProblemsViewPanel")
    internal val SESSION_KEY: DataKey<AIReviewSession> = DataKey.create("AIReview.Session")

    const val PLACE: @NonNls String = "AIReview"
  }

  internal val statusPanel = ErrorStatusPanel(cs)

  init {
    tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    tree.rowHeight = 0
    tree.setExpandableItemsEnabled(false)
    tree.cellRenderer = AIReviewTreeCellRenderer().apply { setupInteractiveHTMLContentSupport(tree) }
    AIReviewFeedback.setup(tree)
    myScrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

    myScrollPane.viewport.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        // Force recalculation of node dimensions because of possible soft-wrapping in the renderer.
        com.intellij.util.ui.tree.TreeUtil.invalidateCacheAndRepaint(tree.getUI())
      }
    })

    firstComponent?.add(BorderLayout.SOUTH, statusPanel)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)

    sink[PANEL_KEY] = this
    sink[SESSION_KEY] = session
  }

  override fun getSortBySeverity(): ToggleOptionAction.Option = mySortBySeverity

  override fun getToolbarActionGroupId(): String = TOOLBAR_GROUP_ID

  override fun getPopupHandlerGroupId(): String = POPUP_GROUP_ID

  override fun customizeTabContent(content: Content) {
    content.apply {
      isCloseable = true
      icon = session.agent?.icon
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      putUserData(ToolWindowContentUi.NOT_SELECTED_TAB_ICON_TRANSPARENT, false)
    }
  }

  override fun createComparator(): Comparator<Node> {
    val superComparator = super.createComparator()
    return Comparator { n1, n2 ->
      when {
        n1 is AIReviewFeedbackNode -> 1
        n2 is AIReviewFeedbackNode -> 1
        else -> superComparator.compare(n1, n2)
      }
    }
  }

  inner class ErrorStatusPanel(cs: CoroutineScope) : JComponent() {
    val reviewState = MutableStateFlow<State?>(null)

    private val statusText = object : StatusText(this) {

      override fun isStatusVisible(): Boolean {
        return reviewState.value is State.Error
      }
    }

    init {
      isOpaque = true
      cs.launch(Dispatchers.EDT) {
        subscribeToStateChanges()
      }
    }

    suspend fun subscribeToStateChanges() {
      reviewState.collectLatest {
        applyState()
      }
    }

    private fun applyState() {
      statusText.clear()
      val errorText = (reviewState.value as? State.Error)?.message
      if (errorText == null) {
        preferredSize = null
        return
      }

      statusText.appendLine(AllIcons.General.Error,
                            errorText,
                            REGULAR_ATTRIBUTES,
                            null
      )
      val request = (reviewState.value as? State.RequestHolder)?.request
      if (!session.canRetryReview(request)) {
        preferredSize = JBDimension(0, JBUI.CurrentTheme.Tree.rowHeight())
        return
      }

      statusText.appendSecondaryText(AIReviewBundle.message("aiReview.problems.analyzing.retry"), LINK_PLAIN_ATTRIBUTES, ActionListener {
        session.retryReview(request)
      })

      preferredSize = JBDimension(0, 2 * JBUI.CurrentTheme.Tree.rowHeight())
    }

    override fun paintChildren(g: Graphics?) {
      if (g == null) return

      when (reviewState.value) {
        is State.Error, is State.Cancelled -> {
          statusText.paint(this, g)
        }
        else -> super.paintChildren(g)
      }
    }
  }
}
