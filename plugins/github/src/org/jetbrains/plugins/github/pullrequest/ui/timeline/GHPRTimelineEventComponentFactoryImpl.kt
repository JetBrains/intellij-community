// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import org.jetbrains.plugins.github.i18n.GithubBundle
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
        builder.append(
          assigned.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.assigned")} ") { "<b>${it.login}</b>" })
      }
      if (unassigned.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" ${GithubBundle.message("pull.request.timeline.and")} ")
        builder.append(
          unassigned.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.unassigned")} ") { "<b>${it.login}</b>" })
      }
      return builder.toString()
    }

    private fun reviewersHTML(added: Collection<GHPullRequestRequestedReviewer> = emptyList(),
                              removed: Collection<GHPullRequestRequestedReviewer> = emptyList()): String {
      val builder = StringBuilder()
      if (added.isNotEmpty()) {
        builder.append(
          added.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.requested.review")} ") { "<b>${it.shortName}</b>" })
      }
      if (removed.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" ${GithubBundle.message("pull.request.timeline.and")} ")
        builder.append(removed.joinToString(
          prefix = "${GithubBundle.message("pull.request.timeline.removed.review.request")} ") { "<b>${it.shortName}</b>" })
      }
      return builder.toString()
    }

    private fun labelsHTML(added: Collection<GHLabel> = emptyList(), removed: Collection<GHLabel> = emptyList()): String {
      val builder = StringBuilder()
      if (added.isNotEmpty()) {
        if (added.size > 1) {
          builder.append(added.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.added.labels")} ") { labelHTML(it) })
        }
        else {
          builder.append(GithubBundle.message("pull.request.timeline.added.label", labelHTML(added.first())))
        }
      }
      if (removed.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" ${GithubBundle.message("pull.request.timeline.and")} ")
        if (removed.size > 1) {
          builder.append(
            removed.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.removed.labels")} ") { labelHTML(it) })
        }
        else {
          builder.append(GithubBundle.message("pull.request.timeline.removed.label", labelHTML(removed.first())))
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

    private fun renameHTML(oldName: String, newName: String) = GithubBundle.message("pull.request.timeline.renamed", oldName, newName)
  }

  private inner class StateEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.State>() {
    override fun createComponent(event: GHPRTimelineEvent.State): Item {
      val icon = when (event.newState) {
        GHPullRequestState.CLOSED -> GithubIcons.PullRequestClosed
        GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
        GHPullRequestState.OPEN -> GithubIcons.PullRequestOpen
      }

      val text = when (event.newState) {
        GHPullRequestState.CLOSED -> GithubBundle.message("pull.request.timeline.closed")
        GHPullRequestState.MERGED -> {
          val mergeEvent = (if (event is GHPRTimelineMergedStateEvents) event.lastStateEvent else event) as GHPRMergedEvent
          if (mergeEvent.commit != null) {
            //language=HTML
            val commitText = """<a href='${mergeEvent.commit.url}'>${mergeEvent.commit.abbreviatedOid}</a>"""
            val ref = branchHTML(mergeEvent.mergeRefName)
            GithubBundle.message("pull.request.timeline.merged.commit", commitText, ref)
          }
          else GithubBundle.message("pull.request.timeline.merged")
        }
        GHPullRequestState.OPEN -> GithubBundle.message("pull.request.timeline.reopened")
      }

      return eventItem(icon, event, text)
    }
  }

  private inner class BranchEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Branch>() {
    override fun createComponent(event: GHPRTimelineEvent.Branch): Item {
      return when (event) {
        is GHPRBaseRefChangedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.changed.base.branch"))
        is GHPRBaseRefForcePushedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.branch.force.pushed", branchHTML(event.ref) ?: "base"))

        is GHPRHeadRefForcePushedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.branch.force.pushed", branchHTML(event.ref) ?: "head"))
        is GHPRHeadRefDeletedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.branch.deleted", branchHTML(event.headRefName)))
        is GHPRHeadRefRestoredEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.branch.head.restored"))

        else -> throwUnknownType(event)
      }
    }

    private fun branchHTML(ref: GHGitRefName?) = ref?.name?.let { branchHTML(it) }
  }

  private inner class ComplexEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Complex>() {
    override fun createComponent(event: GHPRTimelineEvent.Complex): Item {
      return when (event) {
        is GHPRReviewDismissedEvent ->
          Item(GithubIcons.Timeline,
               GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                            GithubBundle.message(
                                                              "pull.request.timeline.stale.review.dismissed",
                                                              event.reviewAuthor?.login
                                                              ?: GithubBundle.message("pull.request.timeline.stale.review.author")),
                                                            event.createdAt),
               event.dismissalMessageHTML?.let {
                 HtmlEditorPane(it).apply {
                   border = JBUI.Borders.emptyLeft(28)
                 }
               })
        is GHPRReadyForReviewEvent ->
          Item(GithubIcons.Review,
               GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                            GithubBundle.message("pull.request.timeline.ready.for.review"),
                                                            event.createdAt))
        is GHPRCrossReferencedEvent -> {
          Item(GithubIcons.Timeline,
               GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                            GithubBundle.message("pull.request.timeline.mentioned"),
                                                            event.createdAt),
               createComponent(event.source))
        }
        is GHPRConnectedEvent -> {
          Item(GithubIcons.Timeline,
               GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                            GithubBundle.message("pull.request.timeline.connected"),
                                                            event.createdAt),
               createComponent(event.subject))
        }
        is GHPRDisconnectedEvent -> {
          Item(GithubIcons.Timeline,
               GHPRTimelineItemComponentFactory.actionTitle(avatarIconsProvider, event.actor,
                                                            GithubBundle.message("pull.request.timeline.disconnected"),
                                                            event.createdAt),
               createComponent(event.subject))
        }

        else -> throwUnknownType(event)
      }
    }
  }

  companion object {
    private fun branchHTML(name: String): String {
      val foreground = CurrentBranchComponent.TEXT_COLOR
      val background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())

      //language=HTML
      return """<span style='color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}'>
                  &nbsp;<icon-inline src='GithubIcons.Branch'/>$name&nbsp;</span>"""
    }

    private fun StringBuilder.appendParagraph(text: String): StringBuilder {
      if (text.isNotEmpty()) this.append("<p>").append(text).append("</p>")
      return this
    }

    private fun createComponent(reference: GHPRReferencedSubject) =
      //language=HTML
      HtmlEditorPane("""${reference.title}&nbsp<a href='${reference.url}'>#${reference.number}</a>""").apply {
        border = JBUI.Borders.emptyLeft(28)
      }
  }
}
