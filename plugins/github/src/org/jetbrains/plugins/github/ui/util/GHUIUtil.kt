// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.UtilBundle
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.util.popup.SelectablePopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SimpleSelectablePopupItemRenderer
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

object GHUIUtil {
  fun getPullRequestStateIcon(state: GHPullRequestState, isDraft: Boolean): Icon =
    if (isDraft) GithubIcons.PullRequestDraft
    else when (state) {
      GHPullRequestState.CLOSED -> CollaborationToolsIcons.PullRequestClosed
      GHPullRequestState.MERGED -> GithubIcons.PullRequestMerged
      GHPullRequestState.OPEN -> CollaborationToolsIcons.PullRequestOpen
    }

  fun getPullRequestStateText(state: GHPullRequestState, isDraft: Boolean): @NlsSafe String =
    if (isDraft) CollaborationToolsBundle.message("review.details.review.state.draft")
    else when (state) {
      GHPullRequestState.CLOSED -> CollaborationToolsBundle.message("review.details.review.state.closed")
      GHPullRequestState.MERGED -> CollaborationToolsBundle.message("review.details.review.state.merged")
      GHPullRequestState.OPEN -> CollaborationToolsBundle.message("review.details.review.state.open")
    }

  fun getIssueStateIcon(state: GithubIssueState): Icon =
    when (state) {
      GithubIssueState.open -> GithubIcons.IssueOpened
      GithubIssueState.closed -> GithubIcons.IssueClosed
    }

  @NlsSafe
  fun getIssueStateText(state: GithubIssueState): String =
    when (state) {
      GithubIssueState.open -> GithubBundle.message("issue.state.open")
      GithubIssueState.closed -> GithubBundle.message("issue.state.closed")
    }

  fun createIssueLabelLabel(label: GHLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    background = CollaborationToolsUIUtil.getLabelBackground(label.color)
    foreground = CollaborationToolsUIUtil.getLabelForeground(background)
  }.andOpaque()

  fun formatActionDate(date: Date): String {
    val prettyDate = DateFormatUtil.formatPrettyDate(date).lowercase(Locale.getDefault())
    val datePrefix = if (prettyDate.equals(UtilBundle.message("date.format.today"), true) ||
                         prettyDate.equals(UtilBundle.message("date.format.yesterday"), true)) ""
    else "on "
    return datePrefix + prettyDate
  }

  fun <T> showChooserPopup(parentComponent: JComponent,
                           presenter: (SelectableWrapper<T>) -> SelectablePopupItemPresentation.Simple,
                           currentList: List<T>,
                           availableListFuture: CompletableFuture<List<T>>)
    : CompletableFuture<CollectionDelta<T>> {

    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = JBList(listModel).apply {
      visibleRowCount = 7
      isFocusable = false
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      background = JBUI.CurrentTheme.Popup.BACKGROUND
    }

    list.cellRenderer = SimpleSelectablePopupItemRenderer.create(presenter)

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      viewport.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = false
    }

    val searchField = SearchTextField(false).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
      textEditor.border = JBUI.Borders.empty()
    }
    AbstractPopup.customizeSearchFieldLook(searchField, true)

    CollaborationToolsUIUtil.attachSearch(list, searchField) {
      presenter(it).shortText
    }

    val panel = JBUI.Panels.simplePanel(scrollPane).addToTop(searchField).apply {
      val size = searchField.preferredSize
      preferredSize = JBDimension(size.width, size.height * 5, true) // default size for loading popup state
    }
    ListUtil.installAutoSelectOnMouseMove(list)

    fun toggleSelection() {
      for (item in list.selectedValuesList) {
        item.isSelected = !item.isSelected
      }
      list.repaint()
    }

    list.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) toggleSelection()
      }
    })

    val originalSelection: Set<T> = currentList.toHashSet()
    listModel.add(currentList.map { SelectableWrapper(it, true) })

    val result = CompletableFuture<CollectionDelta<T>>()
    JBPopupFactory.getInstance().createComponentPopupBuilder(panel, searchField)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setResizable(true)
      .setMovable(true)
      .setKeyboardActions(listOf(Pair.create(ActionListener { toggleSelection() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
      .addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          panel.preferredSize = null // needed that popup does not drop out outside an IDE window
          list.setPaintBusy(true)
          list.emptyText.text = ApplicationBundle.message("label.loading.page.please.wait")

          availableListFuture
            .thenApplyAsync { available ->
              available.map { SelectableWrapper(it, originalSelection.contains(it)) }
                .sortedWith(Comparator.comparing<SelectableWrapper<T>, Boolean> { !it.isSelected }
                              .thenComparing({ presenter(it).shortText }) { a, b -> StringUtil.compare(a, b, true) })
            }.successOnEdt {
              listModel.replaceAll(it)

              list.setPaintBusy(false)
              list.emptyText.text = UIBundle.message("message.noMatchesFound")

              event.asPopup().pack(true, true)

              if (list.selectedIndex == -1) {
                list.selectedIndex = 0
              }
            }
        }

        override fun onClosed(event: LightweightWindowEvent) {
          val selected = listModel.items.filter { it.isSelected }.map { it.value }
          result.complete(CollectionDelta(originalSelection, selected))
        }
      })
      .createPopup()
      .showUnderneathOf(parentComponent)
    return result
  }

  object SelectionPresenters {
    fun PRReviewers(avatarIconsProvider: GHAvatarIconsProvider): (SelectableWrapper<GHPullRequestRequestedReviewer>) -> SelectablePopupItemPresentation.Simple = { wrapper ->
      SelectablePopupItemPresentation.Simple(
        wrapper.value.shortName,
        avatarIconsProvider.getIcon(wrapper.value.avatarUrl, Avatar.Sizes.BASE),
        null,
        isSelected = wrapper.isSelected
      )
    }

    fun Users(avatarIconsProvider: GHAvatarIconsProvider): (SelectableWrapper<GHUser>) -> SelectablePopupItemPresentation.Simple = { wrapper ->
      SelectablePopupItemPresentation.Simple(
        wrapper.value.login,
        avatarIconsProvider.getIcon(wrapper.value.avatarUrl, Avatar.Sizes.BASE),
        null,
        isSelected = wrapper.isSelected
      )
    }

    fun Labels(): (SelectableWrapper<GHLabel>) -> SelectablePopupItemPresentation.Simple = { wrapper ->
      SelectablePopupItemPresentation.Simple(
        wrapper.value.name,
        ColorIcon(16, ColorUtil.fromHex(wrapper.value.color)),
        null,
        isSelected = wrapper.isSelected
      )
    }
  }

  @NlsSafe
  fun getRepositoryDisplayName(allRepositories: List<GHRepositoryCoordinates>,
                               repository: GHRepositoryCoordinates,
                               alwaysShowOwner: Boolean = false): String {
    val showServer = needToShowRepositoryServer(allRepositories)
    val showOwner = if (showServer || alwaysShowOwner) true else needToShowRepositoryOwner(allRepositories)

    val builder = StringBuilder()
    if (showServer) builder.append(repository.serverPath.toUrl(false)).append("/")
    if (showOwner) builder.append(repository.repositoryPath.owner).append("/")
    builder.append(repository.repositoryPath.repository)
    return builder.toString()
  }

  /**
   * Assuming all servers are the same
   */
  private fun needToShowRepositoryOwner(repos: List<GHRepositoryCoordinates>): Boolean {
    if (repos.size <= 1) return false
    val firstOwner = repos.first().repositoryPath.owner
    return repos.any { it.repositoryPath.owner != firstOwner }
  }

  private fun needToShowRepositoryServer(repos: List<GHRepositoryCoordinates>): Boolean {
    if (repos.size <= 1) return false
    val firstServer = repos.first().serverPath
    return repos.any { it.serverPath != firstServer }
  }
}