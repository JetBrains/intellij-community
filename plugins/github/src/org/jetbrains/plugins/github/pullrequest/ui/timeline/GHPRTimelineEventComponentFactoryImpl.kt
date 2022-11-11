// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineItemUIUtil.createItem
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent
import javax.swing.JPanel

class GHPRTimelineEventComponentFactoryImpl(private val avatarIconsProvider: GHAvatarIconsProvider, private val ghostUser: GHUser)
  : GHPRTimelineEventComponentFactory<GHPRTimelineEvent> {

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
      val content = HtmlEditorPane(detailsText).apply {
        border = JBUI.Borders.empty(2, 0)
      }
      return createItem(avatarIconsProvider, event.actor ?: ghostUser, event.createdAt, content)
    }

    protected fun eventItem(event: GHPRTimelineEvent, titleText: @Nls String, detailsText: @Nls String? = null): JComponent {
      val titlePane = HtmlEditorPane(titleText)
      val content = if (detailsText == null) {
        titlePane
      }
      else {
        JPanel(VerticalLayout(4)).apply {
          isOpaque = false
          add(titlePane)
          add(HtmlEditorPane(detailsText).apply {
            border = JBUI.Borders.empty(2, 0)
          })
        }
      }
      return createItem(avatarIconsProvider, event.actor ?: ghostUser, event.createdAt, content)
    }
  }

  private inner class SimpleEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Simple>() {
    override fun createComponent(event: GHPRTimelineEvent.Simple): JComponent {
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

          eventItem(event, builder.toString())
        }
        else -> throwUnknownType(event)
      }
    }

    private fun assigneesHTML(assigned: Collection<GHUser> = emptyList(), unassigned: Collection<GHUser> = emptyList()): @NlsSafe String {
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

    private fun reviewersHTML(added: Collection<GHPullRequestRequestedReviewer?> = emptyList(),
                              removed: Collection<GHPullRequestRequestedReviewer?> = emptyList()): @NlsSafe String {
      val builder = StringBuilder()
      if (added.isNotEmpty()) {
        builder.append(
          added.joinToString(prefix = "${GithubBundle.message("pull.request.timeline.requested.review")} ") {
            "<b>${it?.shortName ?: GithubBundle.message("user.someone")}</b>"
          })
      }
      if (removed.isNotEmpty()) {
        if (builder.isNotEmpty()) builder.append(" ${GithubBundle.message("pull.request.timeline.and")} ")
        builder.append(removed.joinToString(
          prefix = "${GithubBundle.message("pull.request.timeline.removed.review.request")} ") {
          "<b>${it?.shortName ?: GithubBundle.message("user.someone")}</b>"
        })
      }
      return builder.toString()
    }

    private fun labelsHTML(added: Collection<GHLabel> = emptyList(), removed: Collection<GHLabel> = emptyList()): @NlsSafe String {
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
      val background = GHUIUtil.getLabelBackground(label)
      val foreground = GHUIUtil.getLabelForeground(background)
      //language=HTML
      return """<span style='color: #${ColorUtil.toHex(foreground)}; background: #${ColorUtil.toHex(background)}'>
                  &nbsp;${StringUtil.escapeXmlEntities(label.name)}&nbsp;</span>"""
    }

    private fun renameHTML(oldName: String, newName: String) = GithubBundle.message("pull.request.timeline.renamed", oldName, newName)
  }

  private inner class StateEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.State>() {
    override fun createComponent(event: GHPRTimelineEvent.State): JComponent {
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

      return eventItem(event, text)
    }
  }

  private inner class BranchEventComponentFactory : EventComponentFactory<GHPRTimelineEvent.Branch>() {
    override fun createComponent(event: GHPRTimelineEvent.Branch): JComponent {
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
    override fun createComponent(event: GHPRTimelineEvent.Complex): JComponent {
      return when (event) {
        is GHPRReviewDismissedEvent -> {
          val author = (event.reviewAuthor ?: ghostUser).login
          val text = HtmlBuilder()
            .append(GithubBundle.message("pull.request.timeline.stale.review.dismissed", author))
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
          eventItem(event, GithubBundle.message("pull.request.timeline.marked.as.ready"))

        is GHPRConvertToDraftEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.marked.as.draft"))

        is GHPRCrossReferencedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.mentioned"), event.source.asReferenceLink())

        is GHPRConnectedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.connected"), event.subject.asReferenceLink())

        is GHPRDisconnectedEvent ->
          eventItem(event, GithubBundle.message("pull.request.timeline.disconnected"), event.subject.asReferenceLink())

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
                  &nbsp;<icon-inline src='icons.CollaborationToolsIcons.Review.Branch'/>$name&nbsp;</span>"""
    }

    private fun StringBuilder.appendParagraph(text: String): StringBuilder {
      if (text.isNotEmpty()) this.append("<p>").append(text).append("</p>")
      return this
    }

    private fun GHPRReferencedSubject.asReferenceLink(): @NlsSafe String {
      return """${title}&nbsp<a href='${url}'>#${number}</a>"""
    }
  }
}
