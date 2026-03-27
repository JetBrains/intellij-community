// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.ui.BackgroundSupplier
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import java.util.EventObject
import java.util.Objects
import javax.swing.AbstractCellEditor
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreeCellEditor

internal object AIReviewFeedback {

  fun setup(tree: JTree) {
    tree.isEditable = true
    tree.cellEditor = AIReviewTreeCellEditor()
  }

  internal class AIReviewTreeCellEditor : AbstractCellEditor(), TreeCellEditor {

    private val feedbackPanel = FeedbackPanel()

    override fun getTreeCellEditorComponent(
      tree: JTree,
      value: Any,
      isSelected: Boolean,
      isExpanded: Boolean,
      leaf: Boolean,
      row: Int,
    ): Component {
      if (value is AIReviewFeedbackNode) {
        return feedbackPanel
      }

      return value as Component
    }

    override fun getCellEditorValue(): Any {
      return feedbackPanel
    }

    override fun isCellEditable(e: EventObject?): Boolean {
      val tree = e?.source as? JTree
      if (tree == null) return true // enable com.intellij.ide.actions.tree.StartEditingAction

      val node = TreeUtil.getPathForLocation(tree, (e as? MouseEvent)?.x ?: 0, (e as? MouseEvent)?.y ?: 0)?.lastPathComponent
      return node is AIReviewFeedbackNode
    }
  }

  private class FeedbackPanel : JPanel(FlowLayout(FlowLayout.LEFT)) {

    init {
      val label = SimpleColoredComponent()
      label.isOpaque = false
      val text = AIReviewBundle.message("aiReview.fix.result.feedback")
      AccessibleContextUtil.setName(this, text)
      label.append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)

      val likeAction = ActionManager.getInstance().getAction("AIReview.Like")
      val dislikeAction = ActionManager.getInstance().getAction("AIReview.Dislike")
      val likeButton = ActionButton(likeAction, null, AIReviewProblemsViewPanel.PLACE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
      val dislikeButton = ActionButton(dislikeAction, null, AIReviewProblemsViewPanel.PLACE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

      AccessibleContextUtil.setName(likeButton, AIReviewBundle.message("aiReview.action.like.text"))
      AccessibleContextUtil.setName(dislikeButton, AIReviewBundle.message("aiReview.action.dislike.text"))
      label.isFocusable = true
      likeButton.isFocusable = true
      dislikeButton.isFocusable = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(label, likeButton, dislikeButton))

      add(label)
      add(likeButton)
      add(dislikeButton)
    }
  }
}

internal class AIReviewFeedbackNode(parent: Node) : Node(parent), BackgroundSupplier {

  private val background = UIUtil.getTreeBackground(false, false)

  override fun getSelectedElementBackground(row: Int): Color = background

  override fun getLeafState(): LeafState = LeafState.ALWAYS

  override fun update(project: Project, presentation: PresentationData) {}

  override fun getName(): String = "FeedbackNode"

  override fun hashCode(): Int = Objects.hash(project, "feedback")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? AIReviewFeedbackNode ?: return false
    return that.project == project
  }
}
