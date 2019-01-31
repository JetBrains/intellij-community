// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.util.*
import java.awt.Component
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.*

class GithubPullRequestsMetadataServiceImpl internal constructor(private val project: Project,
                                                                 private val progressManager: ProgressManager,
                                                                 private val dataLoader: GithubPullRequestsDataLoader,
                                                                 private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                                 private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                                                 private val serverPath: GithubServerPath,
                                                                 private val repoPath: GithubFullPath)
  : GithubPullRequestsMetadataService {

  private val repoCollaboratorsWithPushAccess: List<GithubUser> by lazy {
    GithubApiPagesLoader
      .loadAll(requestExecutor, progressManager.progressIndicator,
               GithubApiRequests.Repos.Collaborators.pages(serverPath, repoPath.user, repoPath.repository))
      .filter { it.permissions.isPush }
  }

  private val repoIssuesAssignees: List<GithubUser> by lazy {
    GithubApiPagesLoader.loadAll(requestExecutor, progressManager.progressIndicator,
                                 GithubApiRequests.Repos.Assignees.pages(serverPath, repoPath.user, repoPath.repository))
  }

  private val repoIssuesLabels: List<GithubIssueLabel> by lazy {
    GithubApiPagesLoader.loadAll(requestExecutor, progressManager.progressIndicator,
                                 GithubApiRequests.Repos.Labels.pages(serverPath, repoPath.user, repoPath.repository))
  }

  override fun adjustReviewers(pullRequest: Long, parentComponent: JComponent) {
    showUsersChooser(pullRequest, "Reviewers", parentComponent,
                     { _, details -> repoCollaboratorsWithPushAccess.filter { details.user != it } }) { it.requestedReviewers }
      .handleOnEdt(getAdjustmentHandler(pullRequest, "reviewer") { delta, indicator ->
        if (delta.removedItems.isNotEmpty()) {
          indicator.text2 = "Removing reviewers"
          requestExecutor.execute(indicator,
                                  GithubApiRequests.Repos.PullRequests.Reviewers
                                    .remove(serverPath, repoPath.user, repoPath.repository, pullRequest,
                                            delta.removedItems.map { it.login }))
        }
        if (delta.newItems.isNotEmpty()) {
          indicator.text2 = "Adding reviewers"
          requestExecutor.execute(indicator,
                                  GithubApiRequests.Repos.PullRequests.Reviewers
                                    .add(serverPath, repoPath.user, repoPath.repository, pullRequest,
                                         delta.newItems.map { it.login }))
        }
      })
  }

  override fun adjustAssignees(pullRequest: Long, parentComponent: JComponent) {
    showUsersChooser(pullRequest, "Assignees", parentComponent, { _, _ -> repoIssuesAssignees }) { it.assignees }
      .handleOnEdt(getAdjustmentHandler(pullRequest, "assignee") { delta, indicator ->
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.Issues
                                  .updateAssignees(serverPath, repoPath.user, repoPath.repository, pullRequest.toString(),
                                                   delta.newCollection.map { it.login }))
      })
  }

  override fun adjustLabels(pullRequest: Long, parentComponent: JComponent) {
    showChooser(pullRequest, "Labels", parentComponent,
                { SelectionListCellRenderer.Labels() }, { it.name },
                { _, _ -> repoIssuesLabels }, { it.labels.orEmpty() })
      .handleOnEdt(getAdjustmentHandler(pullRequest, "label") { delta, indicator ->
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.Issues.Labels
                                  .replace(serverPath, repoPath.user, repoPath.repository, pullRequest.toString(),
                                           delta.newCollection.map { it.name }))
      })
  }

  private fun showUsersChooser(pullRequest: Long,
                               popupTitle: String,
                               parentComponent: JComponent,
                               availableListProvider: (ProgressIndicator, GithubPullRequestDetailed) -> List<GithubUser>,
                               currentListExtractor: (GithubPullRequestDetailed) -> List<GithubUser>)
    : CompletableFuture<CollectionDelta<GithubUser>> {
    return showChooser(pullRequest, popupTitle, parentComponent, { list ->
      val avatarIconsProvider = avatarIconsProviderFactory.create(JBUI.uiIntValue("GitHub.Avatars.Size", 20), list)
      SelectionListCellRenderer.Users(avatarIconsProvider)
    }, { it.login }, availableListProvider, currentListExtractor)
  }

  private fun <T> showChooser(pullRequest: Long,
                              popupTitle: String,
                              parentComponent: JComponent,
                              cellRendererFactory: (JList<SelectableWrapper<T>>) -> ListCellRenderer<SelectableWrapper<T>>,
                              speedSearchNamer: (T) -> String,
                              availableListProvider: (ProgressIndicator, GithubPullRequestDetailed) -> List<T>,
                              currentListExtractor: (GithubPullRequestDetailed) -> List<T>)
    : CompletableFuture<CollectionDelta<T>> {

    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = JBList<SelectableWrapper<T>>(listModel)

    val builder = PopupChooserBuilder<SelectableWrapper<T>>(list)
      .setTitle(popupTitle)
      .setResizable(true)
      .setMovable(true)
      .setNamerForFiltering { speedSearchNamer(it.value) }
      .setAutoSelectIfEmpty(false)
      .setCloseOnEnter(false)
      .setRenderer(cellRendererFactory(list))
      .setItemsChosenCallback {
        for (item in it) {
          item.selected = !item.selected
        }
        list.repaint()
      }
    val popup = builder.createPopup()

    val updater = builder.backgroundUpdater
    updater.paintBusy(true)
    list.emptyText.text = "Loading..."

    var originalSelection: Set<T> = setOf()

    val progressIndicator = EmptyProgressIndicator()
    Disposer.register(popup, Disposable { progressIndicator.cancel() })

    val loadingFuture = GithubAsyncUtil
      .futureOfMutable { dataLoader.getDataProvider(pullRequest).detailsRequest }
      .thenComposeAsync(Function { details: GithubPullRequestDetailedWithHtml ->
        originalSelection = currentListExtractor(details).toHashSet()
        progressManager.submitBackgroundTask(project, "Load List Of Possibilities", true, progressIndicator) {
          availableListProvider(it, details)
        }
      })
      .thenAcceptAsync(Consumer { possibilities ->
        listModel.replaceAll(possibilities
                               .map { SelectableWrapper(it, originalSelection.contains(it)) }
                               .sortedBy { !it.selected })

        updater.paintBusy(false)
        list.emptyText.text = StatusText.DEFAULT_EMPTY_TEXT

        popup.pack(true, true)
      }, EDT_EXECUTOR)
      .exceptionally {
        updater.paintBusy(false)
        list.emptyText.clear()
        list.emptyText.appendText("Can't load the list", SimpleTextAttributes.ERROR_ATTRIBUTES)
        list.emptyText.appendSecondaryText(it.message.orEmpty(), SimpleTextAttributes.ERROR_ATTRIBUTES, null)
        throw it
      }

    Disposer.register(popup, Disposable {
      loadingFuture.cancel(true)
    })

    val result = CompletableFuture<CollectionDelta<T>>()
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (!loadingFuture.isDone || loadingFuture.isCancelled || loadingFuture.isCompletedExceptionally) {
          result.cancel(true)
          return
        }

        val selected = listModel.items.filter { it.selected }.map { it.value }
        result.complete(CollectionDelta(originalSelection, selected))
      }
    })
    popup.showUnderneathOf(parentComponent)
    return result
  }

  private fun <T> getAdjustmentHandler(pullRequest: Long,
                                       entityName: String,
                                       adjuster: (CollectionDelta<T>, ProgressIndicator) -> Unit): (CollectionDelta<T>?, Throwable?) -> Unit {
    return handler@{ delta, error ->
      if (error != null) {
        if (!GithubAsyncUtil.isCancellation(error))
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        return@handler
      }
      if (delta == null || delta.isEmpty) {
        return@handler
      }

      if (!busyStateTracker.acquire(pullRequest)) return@handler
      progressManager.run(object : Task.Backgroundable(project, "Adjusting List Of ${StringUtil.pluralize(entityName).capitalize()}",
                                                       true) {
        override fun run(indicator: ProgressIndicator) {
          adjuster(delta, indicator)
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        }

        override fun onFinished() {
          busyStateTracker.release(pullRequest)
          dataLoader.reloadDetails(pullRequest)
        }
      })
    }
  }

  private data class SelectableWrapper<T>(val value: T, var selected: Boolean = false)

  private sealed class SelectionListCellRenderer<T>
    : ListCellRenderer<SelectableWrapper<T>>, BorderLayoutPanel() {

    private val mainLabel = JLabel()
    private val checkIconLabel = JLabel()

    init {
      checkIconLabel.iconTextGap = JBUI.scale(UIUtil.DEFAULT_VGAP)

      addToLeft(checkIconLabel)
      addToCenter(mainLabel)

      border = JBUI.Borders.empty(2)
    }

    override fun getListCellRendererComponent(list: JList<out SelectableWrapper<T>>,
                                              value: SelectableWrapper<T>,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      font = list.font
      foreground = UIUtil.getListForeground(isSelected, list.hasFocus())
      background = UIUtil.getListBackground(isSelected, list.hasFocus())

      mainLabel.foreground = foreground
      mainLabel.font = font

      mainLabel.text = getText(value.value)
      mainLabel.icon = getIcon(value.value)

      if (value.selected) {
        checkIconLabel.icon = AllIcons.Actions.Checked
        checkIconLabel.border = JBUI.Borders.empty(0, 4)
      }
      else {
        checkIconLabel.icon = null
        checkIconLabel.border = JBUI.Borders.empty(0, 10)
      }

      return this
    }

    abstract fun getText(value: T): String
    abstract fun getIcon(value: T): Icon

    class Users(private val iconsProvider: CachingGithubAvatarIconsProvider)
      : SelectionListCellRenderer<GithubUser>() {

      override fun getText(value: GithubUser) = value.login
      override fun getIcon(value: GithubUser) = iconsProvider.getIcon(value)

    }

    class Labels
      : SelectionListCellRenderer<GithubIssueLabel>() {

      override fun getText(value: GithubIssueLabel) = value.name
      override fun getIcon(value: GithubIssueLabel) = ColorIcon(16, ColorUtil.fromHex(value.color))

    }
  }

  private class CollectionDelta<out T>(oldCollection: Collection<T>, val newCollection: Collection<T>) {
    val newItems: Collection<T> = newCollection - oldCollection
    val removedItems: Collection<T> = oldCollection - newCollection

    val isEmpty = newItems.isEmpty() && removedItems.isEmpty()
  }
}