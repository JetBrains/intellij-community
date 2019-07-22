// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBInsets
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRTimelineComponent(private val model: ListModel<GHPRTimelineItem>)
  : JBPanelWithEmptyText(VerticalFlowLayout()) {

  init {
    isOpaque = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          remove(i)
        }
        revalidate()
        repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          add(createComponent(model.getElementAt(i)), i)
        }
        validate()
        repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          remove(i)
          add(createComponent(model.getElementAt(i)), i)
        }
        revalidate()
        repaint()
      }
    })

    for (i in 0 until model.size) {
      add(createComponent(model.getElementAt(i)), i)
    }
  }

  override fun getPreferredSize(): Dimension? {
    if (model.size == 0 && !StringUtil.isEmpty(emptyText.text)) {
      val s = emptyText.preferredSize
      JBInsets.addTo(s, insets)
      return s
    }
    else {
      return super.getPreferredSize()
    }
  }

  private fun createComponent(item: GHPRTimelineItem): JComponent {
    if (item is GHPRTimelineItem.Unknown)
      return JLabel("Unknown type:" + item.__typename)
    else {
      val text = when (item) {
        is GHPullRequestCommit -> """Commit "${item.commit.messageHeadlineHTML}" by ${item.commit.author?.name}"""
        is GHPullRequestReview -> """${item.author?.login} added review with text "${item.bodyHTML}"""
        is GHIssueComment -> """Comment "${item.bodyHtml}" by ${item.author?.login}"""

        is GHPRRenamedTitleEvent -> """${item.actor?.login} renamed from "${item.previousTitle}" to "${item.currentTitle}""""

        is GHPRAssignedEvent -> """${item.actor?.login} assigned ${item.user.login}"""
        is GHPRUnassignedEvent -> """${item.actor?.login} unassigned ${item.user.login}"""

        is GHPRLabeledEvent -> """${item.actor?.login} added label ${item.label.name}"""
        is GHPRUnlabeledEvent -> """${item.actor?.login} removed label ${item.label.name}"""

        is GHPRReviewRequestedEvent -> """${item.actor?.login} added reviewer ${item.requestedReviewer}"""
        is GHPRReviewUnrequestedEvent -> """${item.actor?.login} removed reviewer ${item.requestedReviewer}"""

        is GHPRClosedEvent -> """${item.actor?.login} closed PR"""
        is GHPRReopenedEvent -> """${item.actor?.login} reopened PR"""
        is GHPRMergedEvent -> """${item.actor?.login} merged PR"""

        is GHPRReviewDismissedEvent -> """${item.actor?.login} dismissed review by ${item.reviewAuthor} with message "${item.dismissalMessageHTML}" """

        is GHPRBaseRefChangedEvent -> """${item.actor?.login} changed the base branch"""
        is GHPRBaseRefForcePushedEvent -> """${item.actor?.login} force-pushed the branch ${item.ref?.name} from ${item.beforeCommit.abbreviatedOid} to ${item.afterCommit.abbreviatedOid}"""

        is GHPRHeadRefForcePushedEvent -> """${item.actor?.login} force-pushed the branch ${item.ref?.name} from ${item.beforeCommit.abbreviatedOid} to ${item.afterCommit.abbreviatedOid}"""

        is GHPRHeadRefDeletedEvent -> """${item.actor?.login} deleted the branch ${item.headRefName}"""
        is GHPRHeadRefRestoredEvent -> """${item.actor?.login} restored the head branch"""

        is GHPRTimelineMergedSimpleEvents -> """${item.actor?.login} performed multiple simple actions on ${item.createdAt}"""
        is GHPRTimelineMergedStateEvents -> """${item.actor?.login} performed multiple actions changing state on ${item.createdAt}"""

        else -> item.javaClass.canonicalName
      }
      return JLabel(text)
    }
  }
}