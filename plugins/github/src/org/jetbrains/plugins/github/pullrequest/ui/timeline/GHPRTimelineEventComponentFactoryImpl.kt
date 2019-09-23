// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemComponentFactory.Item
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.Icon

class GHPRTimelineEventComponentFactoryImpl(private val avatarIconsProvider: GHAvatarIconsProvider)
  : GHPRTimelineEventComponentFactory<GHPRTimelineEvent> {

  private val simpleEventDelegate = SimpleEventComponentFactory()
  private val stateEventDelegate = StateEventComponentFactory()
  private val branchEventDelegate = BranchEventComponentFactory()
  private val complexEventDelegate = ComplexEventComponentFactory()

  override fun createComponent(event: GHPRTimelineEvent): Item {
    return when (event) {
      is GHPRTimelineEvent.Simple -> simpleEventDelegate.createComponent(event)
      is GHPRTimelineEvent.State -> stateEventDelegate.createComponent(event)
      is GHPRTimelineEvent.Branch -> branchEventDelegate.createComponent(event)
      is GHPRTimelineEvent.Complex -> complexEventDelegate.createComponent(event)
      else -> throwUnknownType(event)
    }
  }

  private fun throwUnknownType(item: GHPRTimelineEvent): Nothing {
    throw IllegalStateException("""Unknown event type "${item.javaClass.canonicalName}"""")
  }

  private abstract inner class EventComponentFactory<T : GHPRTimelineEvent> : GHPRTimelineEventComponentFactory<T> {

    protected fun eventItem(item: GHPRTimelineEvent, @Language("HTML") titleHTML: String): Item {
      return eventItem(GithubIcons.Timeline, item, titleHTML)
    }

    protected fun eventItem(markerIcon: Icon,
                            item: GHPRTimelineEvent,
                            @Language("HTML") titleHTML: String): Item {
      return Item(markerIcon, GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, item.actor, titleHTML, item.createdAt))
    }
  }

  private inner class SimpleEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Simple>() {
    override fun createComponent(event: GHPRTimelineEvent.Simple): Item {
      return when (event) {
        is GHPRAssignedEvent ->
          eventItem(event, assigneesHTML(assigned = listOf(event.user)))
        is GHPRUnassignedEvent ->
          eventItem(event, assigneesHTML(unassigned = listOf(event.user)))

        is GHPRReviewRequestedEvent ->
          eventItem(event, reviewersHTML(added = listOf(event.requestedReviewer)))
        is GHPRReviewUnrequestedEvent ->
          eventItem(event, reviewersHTML(removed = listOf(event.requestedReviewer)))

        is GHPRLabeledEvent ->
          eventItem(event, labelsHTML(added = listOf(event.label)))
        is GHPRUnlabeledEvent ->
          eventItem(event, labelsHTML(removed = listOf(event.label)))

        is GHPRRenamedTitleEvent ->
          eventItem(event, renameHTML(event.previousTitle, event.currentTitle))

        is GHPRTimelineMergedSimpleEvents -> {
          val builder = StringBuilder()
            .appendParagraph(labelsHTML(event.addedLabels, event.removedLabels))
            .appendParagraph(assigneesHTML(event.assignedPeople, event.unassignedPeople))
            .appendParagraph(reviewersHTML(event.addedReviewers, event.removedReviewers))
            .appendParagraph(event.rename?.let { renameHTML(it.first, it.second) }.orEmpty())

          Item(GithubIcons.Timeline, GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor, "", event.createdAt),
               HtmlEditorPane(builder.toString()).apply {
                 border = JBUI.Borders.emptyLeft(28)
                 foreground = UIUtil.getContextHelpForeground()
               })
        }
        else -> throwUnknownType(event)
      }
    }

    private fun assigneesHTML(assigned: Collection<GHUser> = emptyList(), unassigned: Collection<GHUser> = emptyList()): String {
      val builder = StringBuilder()
      if (assigned.isNotEmpty()) {
        builder.append(assigned.joinToString(prefix = "assigned ") { "<b>${it.login}</b>" })
      }
      if (unassigned.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" and ")
        builder.append(unassigned.joinToString(prefix = "unassigned ") { "<b>${it.login}</b>" })
      }
      return builder.toString()
    }

    private fun reviewersHTML(added: Collection<GHPullRequestReviewer> = emptyList(),
                              removed: Collection<GHPullRequestReviewer> = emptyList()): String {
      val builder = StringBuilder()
      if (added.isNotEmpty()) {
        builder.append(added.joinToString(prefix = "requested a review from ") { "<b>${extractReviewerName(it)}</b>" })
      }
      if (removed.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" and ")
        builder.append(removed.joinToString(prefix = "removed review request from ") { "<b>${extractReviewerName(it)}</b>" })
      }
      return builder.toString()
    }

    private fun extractReviewerName(reviewer: GHPullRequestReviewer): String {
      return when (reviewer) {
        is GHUser -> reviewer.login
        is GHPullRequestReviewer.Team -> "team"
        else -> throw IllegalArgumentException()
      }
    }

    private fun labelsHTML(added: Collection<GHLabel> = emptyList(), removed: Collection<GHLabel> = emptyList()): String {
      val builder = StringBuilder()
      if (added.isNotEmpty()) {
        if (added.size > 1) {
          builder.append(added.joinToString(prefix = "added labels ") { labelHTML(it) })
        }
        else {
          builder.append("added the ").append(labelHTML(added.first())).append(" label")
        }
      }
      if (removed.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" and ")
        if (removed.size > 1) {
          builder.append(removed.joinToString(prefix = "removed labels ") { labelHTML(it) })
        }
        else {
          builder.append("removed the ").append(labelHTML(removed.first())).append(" label")
        }
      }
      return builder.toString()
    }

    private fun labelHTML(label: GHLabel): String {
      val background = GithubUIUtil.getLabelBackground(label)
      val foreground = GithubUIUtil.getLabelForeground(background)
      //language=HTML
      return """<span style='color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}'>
                  &nbsp;${StringUtil.escapeXmlEntities(label.name)}&nbsp;</span>"""
    }

    private fun renameHTML(oldName: String, newName: String) = "renamed this from <b>$oldName</b> to <b>$newName</b>"
  }

  private inner class StateEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.State>() {
    override fun createComponent(event: GHPRTimelineEvent.State): Item {
      val icon = when (event.newState) {
        GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
        GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
        GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
      }

      val text = when (event.newState) {
        GHPullRequestState.CLOSED -> "closed this"
        GHPullRequestState.MERGED -> "merged this"
        GHPullRequestState.OPEN -> "reopened this"
      }

      return eventItem(icon, event, text)
    }
  }

  private inner class BranchEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Branch>() {
    override fun createComponent(event: GHPRTimelineEvent.Branch): Item {
      return when (event) {
        is GHPRBaseRefChangedEvent ->
          eventItem(event, "changed the base branch")
        is GHPRBaseRefForcePushedEvent ->
          eventItem(event, "force-pushed the ${branchHTML(event.ref) ?: "base"} branch")

        is GHPRHeadRefForcePushedEvent ->
          eventItem(event, "force-pushed the ${branchHTML(event.ref) ?: "head"} branch")
        is GHPRHeadRefDeletedEvent ->
          eventItem(event, "deleted the ${branchHTML(event.headRefName)} branch")
        is GHPRHeadRefRestoredEvent ->
          eventItem(event, "restored head branch")

        else -> throwUnknownType(event)
      }
    }

    private fun branchHTML(ref: GHGitRefName?) = ref?.name?.let { branchHTML(it) }

    //language=HTML
    private fun branchHTML(name: String): String {
      val foreground = CurrentBranchComponent.TEXT_COLOR
      val background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())

      return """<span style='color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}'>
                  &nbsp;<icon-inline src='GithubIcons.Branch'/>$name&nbsp;</span>"""
    }
  }

  private inner class ComplexEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Complex>() {
    override fun createComponent(event: GHPRTimelineEvent.Complex): Item {
      return when (event) {
        is GHPRReviewDismissedEvent ->
          Item(GithubIcons.Timeline, GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                                                  "dismissed <b>${event.reviewAuthor?.login}</b>`s stale review",
                                                                                  event.createdAt),
               event.dismissalMessageHTML?.let {
                 HtmlEditorPane(it).apply {
                   border = JBUI.Borders.emptyLeft(28)
                 }
               })

        else -> throwUnknownType(event)
      }
    }
  }

  companion object {
    private fun StringBuilder.appendParagraph(text: String): StringBuilder {
      if (text.isNotEmpty()) this.append("<p>").append(text).append("</p>")
      return this
    }
  }
}
