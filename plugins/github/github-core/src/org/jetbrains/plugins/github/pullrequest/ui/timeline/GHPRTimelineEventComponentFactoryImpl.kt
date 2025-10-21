// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter.Companion.OPEN_PR_LINK_PREFIX
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createDescriptionComponent
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createTimelineItem
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import javax.swing.JComponent

internal class GHPRTimelineEventComponentFactoryImpl(
  private val timelineVm: GHPRTimelineViewModel
) : GHPRTimelineEventComponentFactory<GHPRTimelineEvent> {

  private val avatarIconsProvider: GHAvatarIconsProvider = timelineVm.avatarIconsProvider
  private val htmlImageLoader = timelineVm.htmlImageLoader
  private val ghostUser: GHUser = timelineVm.ghostUser

  private val simpleEventDelegate = SimpleEventComponentFactory()
  private val stateEventDelegate = StateEventComponentFactory()
  private val branchEventDelegate = BranchEventComponentFactory()
  private val complexEventDelegate = ComplexEventComponentFactory()

  override fun createComponent(event: GHPRTimelineEvent): JComponent {
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
    protected fun eventItem(event: GHPRTimelineEvent, detailsText: @Nls String): JComponent {
      val content = createDescriptionComponent(detailsText, prLinkHandler = timelineVm::openPullRequestInfoAndTimeline)
      return createTimelineItem(avatarIconsProvider, event.actor ?: ghostUser, event.createdAt, content)
    }

    protected fun eventItem(event: GHPRTimelineEvent, type: StatusMessageType, detailsText: @Nls String): JComponent {
      val content = createDescriptionComponent(detailsText, type, prLinkHandler = timelineVm::openPullRequestInfoAndTimeline)
      return createTimelineItem(avatarIconsProvider, event.actor ?: ghostUser, event.createdAt, content)
    }

    protected fun eventItem(event: GHPRTimelineEvent, titleText: @Nls String, detailsText: @Nls String? = null): JComponent {
      val titlePane = SimpleHtmlPane(customImageLoader = htmlImageLoader).apply {
        setHtmlBody(titleText)
      }
      val content = if (detailsText == null) {
        titlePane
      }
      else {
        VerticalListPanel(4).apply {
          add(titlePane)
          add(createDescriptionComponent(detailsText, prLinkHandler = timelineVm::openPullRequestInfoAndTimeline))
        }
      }
      return createTimelineItem(avatarIconsProvider, event.actor ?: ghostUser, event.createdAt, content)
    }
  }

  private inner class SimpleEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Simple>() {
    override fun createComponent(event: GHPRTimelineEvent.Simple): JComponent {
      return when (event) {
        is GHPRAssignedEvent ->
          eventItem(event, assigneesText(assigned = listOf(event.user)))
        is GHPRUnassignedEvent ->
          eventItem(event, assigneesText(unassigned = listOf(event.user)))

        is GHPRReviewRequestedEvent ->
          eventItem(event, reviewersText(added = listOf(event.requestedReviewer)))
        is GHPRReviewUnrequestedEvent ->
          eventItem(event, reviewersText(removed = listOf(event.requestedReviewer)))

        is GHPRLabeledEvent ->
          eventItem(event, labelsText(added = listOf(event.label)))
        is GHPRUnlabeledEvent ->
          eventItem(event, labelsText(removed = listOf(event.label)))

        is GHPRRenamedTitleEvent ->
          eventItem(event, renameText(event.previousTitle, event.currentTitle))

        is GHPRTimelineMergedSimpleEvents -> {
          val builder = HtmlBuilder()
            .appendParagraph(labelsText(event.addedLabels, event.removedLabels))
            .appendParagraph(assigneesText(event.assignedPeople, event.unassignedPeople))
            .appendParagraph(reviewersText(event.addedReviewers, event.removedReviewers))
            .appendParagraph(event.rename?.let { renameText(it.first, it.second) }.orEmpty())

          eventItem(event, builder.toString())
        }
        else -> throwUnknownType(event)
      }
    }

    private fun assigneesText(assigned: Collection<GHUser> = emptyList(), unassigned: Collection<GHUser> = emptyList()): @Nls String {
      val assignedNames = assigned.takeIf { it.isNotEmpty() }?.joinToString { actorHTML(it).toString() }
      val unassignedNames = unassigned.takeIf { it.isNotEmpty() }?.joinToString { actorHTML(it).toString() }

      return when {
        assignedNames != null && unassignedNames != null ->
          message("pull.request.timeline.event.assigned.and.unassigned", assignedNames, unassignedNames)
        assignedNames != null ->
          message("pull.request.timeline.event.assigned", assignedNames)
        unassignedNames != null ->
          message("pull.request.timeline.event.unassigned", unassignedNames)
        else -> ""
      }
    }

    private fun reviewersText(added: Collection<GHPullRequestRequestedReviewer?> = emptyList(),
                              removed: Collection<GHPullRequestRequestedReviewer?> = emptyList()): @NlsSafe String {
      val addedLogins = added.takeIf { it.isNotEmpty() }?.joinToString { reviewerHTML(it ?: ghostUser).toString() }
      val removedLogins = removed.takeIf { it.isNotEmpty() }?.joinToString { reviewerHTML(it ?: ghostUser).toString() }

      return when {
        addedLogins != null && removedLogins != null ->
          message("pull.request.timeline.event.requested.review.and.removed.review.request", addedLogins, removedLogins)
        addedLogins != null ->
          message("pull.request.timeline.event.requested.review", addedLogins)
        removedLogins != null ->
          message("pull.request.timeline.event.removed.review.request", removedLogins)
        else -> ""
      }
    }

    private fun labelsText(added: Collection<GHLabel> = emptyList(), removed: Collection<GHLabel> = emptyList()): @Nls String {
      val addedLabels = added.takeIf { it.isNotEmpty() }?.joinToString { labelHTML(it).toString() }
      val removedLabels = removed.takeIf { it.isNotEmpty() }?.joinToString { labelHTML(it).toString() }

      return when {
        addedLabels != null && removedLabels != null ->
          message("pull.request.timeline.event.labels.added.and.removed", addedLabels, removedLabels)
        addedLabels != null ->
          message("pull.request.timeline.event.labels.added", addedLabels)
        removedLabels != null ->
          message("pull.request.timeline.event.labels.removed", removedLabels)
        else -> ""
      }
    }

    private fun renameText(oldName: String, newName: String): @Nls String = message("pull.request.timeline.renamed", oldName, newName)
  }

  private inner class StateEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.State>() {
    override fun createComponent(event: GHPRTimelineEvent.State): JComponent {
      return when (event.newState) {
        GHPullRequestState.CLOSED -> {
          eventItem(event, StatusMessageType.SECONDARY_INFO, message("pull.request.timeline.closed"))
        }
        GHPullRequestState.MERGED -> {
          val mergeEvent = (if (event is GHPRTimelineMergedStateEvents) event.lastStateEvent else event) as GHPRMergedEvent
          val text = if (mergeEvent.commit != null) {
            val commitText = HtmlChunk.link(mergeEvent.commit.url, mergeEvent.commit.abbreviatedOid).toString()
            val ref = branchHTML(mergeEvent.mergeRefName)
            message("pull.request.timeline.merged.commit", commitText, ref)
          }
          else message("pull.request.timeline.merged")
          eventItem(event, StatusMessageType.SUCCESS, text)
        }
        GHPullRequestState.OPEN -> {
          eventItem(event, message("pull.request.timeline.reopened"))
        }
      }
    }
  }

  private inner class BranchEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Branch>() {
    override fun createComponent(event: GHPRTimelineEvent.Branch): JComponent {
      return when (event) {
        is GHPRBaseRefChangedEvent ->
          eventItem(event, message("pull.request.timeline.changed.base.branch"))
        is GHPRBaseRefForcePushedEvent ->
          eventItem(event, message("pull.request.timeline.branch.force.pushed", branchHTML(event.ref) ?: "base"))

        is GHPRHeadRefForcePushedEvent ->
          eventItem(event, message("pull.request.timeline.branch.force.pushed", branchHTML(event.ref) ?: "head"))
        is GHPRHeadRefDeletedEvent ->
          eventItem(event, message("pull.request.timeline.branch.deleted", branchHTML(event.headRefName)))
        is GHPRHeadRefRestoredEvent ->
          eventItem(event, message("pull.request.timeline.branch.head.restored"))

        else -> throwUnknownType(event)
      }
    }

    private fun branchHTML(ref: GHGitRefName?) = ref?.name?.let { branchHTML(it) }
  }

  private inner class ComplexEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Complex>() {
    override fun createComponent(event: GHPRTimelineEvent.Complex): JComponent {
      return when (event) {
        is GHPRReviewDismissedEvent -> {
          val author = (event.reviewAuthor ?: ghostUser).login
          val text = HtmlBuilder()
            .append(message("pull.request.timeline.stale.review.dismissed", author))
            .apply {
              val msg = event.dismissalMessageHTML
              if (msg != null) {
                append(HtmlChunk.br())
                  .appendRaw(msg)
              }
            }.toString()
          eventItem(event, text)
        }

        is GHPRReadyForReviewEvent ->
          eventItem(event, message("pull.request.timeline.marked.as.ready"))

        is GHPRConvertToDraftEvent ->
          eventItem(event, message("pull.request.timeline.marked.as.draft"))

        is GHPRCrossReferencedEvent ->
          eventItem(event, message("pull.request.timeline.mentioned"), event.source.asReferenceLink())

        is GHPRConnectedEvent ->
          eventItem(event, message("pull.request.timeline.connected"), event.subject.asReferenceLink())

        is GHPRDisconnectedEvent ->
          eventItem(event, message("pull.request.timeline.disconnected"), event.subject.asReferenceLink())

        else -> throwUnknownType(event)
      }
    }
  }

  companion object {
    private fun branchHTML(name: @Nls String): HtmlChunk {
      val foreground = CurrentBranchComponent.TEXT_COLOR
      val background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())

      val iconChunk = HtmlChunk
        .tag("icon-inline")
        .attr("src", "icons.DvcsImplIcons.BranchLabel")
      return HtmlChunk.span("color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}")
        .child(HtmlChunk.nbsp())
        .child(iconChunk)
        .addText(name)
        .child(HtmlChunk.nbsp())
    }

    private fun labelHTML(label: GHLabel): HtmlChunk {
      val background = CollaborationToolsUIUtil.getLabelBackground(label.color)
      val foreground = CollaborationToolsUIUtil.getLabelForeground(background)

      return HtmlChunk.span("color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}")
        .child(HtmlChunk.nbsp())
        .addText(label.name)
        .child(HtmlChunk.nbsp())
    }

    private fun actorHTML(actor: GHActor): HtmlChunk {
      return HtmlChunk.link(actor.url, actor.getPresentableName())
    }

    private fun reviewerHTML(reviewer: GHPullRequestRequestedReviewer): HtmlChunk {
      return HtmlChunk.link(reviewer.url, reviewer.getPresentableName())
    }

    private fun HtmlBuilder.appendParagraph(text: @Nls String): HtmlBuilder = apply {
      if (text.isNotEmpty()) {
        append(HtmlChunk.p().addRaw(text))
      }
    }

    private fun GHPRReferencedSubject.asReferenceLink(): @NlsSafe String {
      val linkTarget = when (this) {
        is GHPRReferencedSubject.PullRequest -> "$OPEN_PR_LINK_PREFIX${number}"
        else -> url
      }

      return HtmlChunk.div().children(
        HtmlChunk.text(title),
        HtmlChunk.nbsp(),
        HtmlChunk.link(linkTarget, "#$number")
      ).toString()
    }
  }
}
