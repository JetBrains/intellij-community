// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.*
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubUser
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsListLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsRepositoryDataLoader
import org.jetbrains.plugins.github.util.*
import java.awt.Component
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.*
import javax.swing.event.DocumentEvent

class GithubPullRequestsMetadataServiceImpl internal constructor(private val project: Project,
                                                                 private val progressManager: ProgressManager,
                                                                 private val repoDataLoader: GithubPullRequestsRepositoryDataLoader,
                                                                 private val listLoader: GithubPullRequestsListLoader,
                                                                 private val dataLoader: GithubPullRequestsDataLoader,
                                                                 private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                                                 private val requestExecutor: GithubApiRequestExecutor,
                                                                 private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                                                 private val serverPath: GithubServerPath,
                                                                 private val repoPath: GithubFullPath)
  : GithubPullRequestsMetadataService {

  override fun adjustReviewers(pullRequest: Long, parentComponent: JComponent) {
    showUsersChooser(pullRequest, "Reviewers", parentComponent,
                     { _, details -> repoDataLoader.collaboratorsWithPushAccess.filter { details.user != it } }) { it.requestedReviewers }
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
    showUsersChooser(pullRequest, "Assignees", parentComponent, { _, _ -> repoDataLoader.issuesAssignees }) { it.assignees }
      .handleOnEdt(getAdjustmentHandler(pullRequest, "assignee") { delta, indicator ->
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.Issues
                                  .updateAssignees(serverPath, repoPath.user, repoPath.repository, pullRequest.toString(),
                                                   delta.newCollection.map { it.login }))
      })
  }

  override fun adjustLabels(pullRequest: Long, parentComponent: JComponent) {
    showChooser(pullRequest, "Labels", parentComponent,
                { SelectionListCellRenderer.Labels() }, { _, _ -> repoDataLoader.issuesLabels }, { it.labels.orEmpty() })
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
    }, availableListProvider, currentListExtractor)
  }

  private fun <T> showChooser(pullRequest: Long,
                              popupTitle: String,
                              parentComponent: JComponent,
                              cellRendererFactory: (JList<SelectableWrapper<T>>) -> SelectionListCellRenderer<T>,
                              availableListProvider: (ProgressIndicator, GithubPullRequestDetailed) -> List<T>,
                              currentListExtractor: (GithubPullRequestDetailed) -> List<T>)
    : CompletableFuture<CollectionDelta<T>> {

    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = JBList<SelectableWrapper<T>>().apply {
      visibleRowCount = 7
      isFocusable = false
      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    val listCellRenderer = cellRendererFactory(list)
    list.cellRenderer = listCellRenderer

    val speedSearch = SpeedSearch()
    val filteringListModel = NameFilteringListModel<SelectableWrapper<T>>(
      listModel, { listCellRenderer.getText(it.value) }, speedSearch::shouldBeShowing, { speedSearch.filter ?: "" })
    list.model = filteringListModel

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      viewport.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = false
    }

    val searchField = SearchTextField(false).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
      textEditor.border = JBUI.Borders.empty()
      //focus dark magic, otherwise focus shifts to searchfield panel
      isFocusable = false
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          speedSearch.updatePattern(text)
        }
      })
    }

    val panel = simplePanel(scrollPane).addToTop(searchField)
    ScrollingUtil.installActions(list, panel)
    ListUtil.installAutoSelectOnMouseMove(list)

    fun toggleSelection() {
      for (item in list.selectedValuesList) {
        item.selected = !item.selected
      }
      list.repaint()
    }

    list.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) toggleSelection()
      }
    })

    val result = CompletableFuture<CollectionDelta<T>>()
    JBPopupFactory.getInstance().createComponentPopupBuilder(panel, searchField)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setTitle(popupTitle)
      .setResizable(true)
      .setMovable(true)
      .setKeyboardActions(listOf(Pair.create(ActionListener { toggleSelection() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
      .addListener(object : JBPopupListener {
        private lateinit var loadingFuture: CompletableFuture<Void>
        private lateinit var originalSelection: Set<T>

        override fun beforeShown(event: LightweightWindowEvent) {
          val popup = event.asPopup()

          list.setPaintBusy(true)
          list.emptyText.text = "Loading..."

          val progressIndicator = EmptyProgressIndicator()

          loadingFuture = dataLoader.getDataProvider(pullRequest).detailsRequest
            .thenComposeAsync(Function { details: GithubPullRequestDetailedWithHtml ->
              originalSelection = currentListExtractor(details).toHashSet()
              progressManager.submitBackgroundTask(project, "Load List Of Possibilities", true, progressIndicator) { indicator ->
                availableListProvider(indicator, details)
                  .map { SelectableWrapper(it, originalSelection.contains(it)) }
                  .sortedWith(Comparator.comparing<SelectableWrapper<T>, Boolean> { !it.selected }
                                .thenComparing({ listCellRenderer.getText(it.value) }) { a, b -> StringUtil.compare(a, b, true) })
              }
            })
            .thenAcceptAsync(Consumer { possibilities ->
              listModel.replaceAll(possibilities)

              list.setPaintBusy(false)
              list.emptyText.text = UIBundle.message("message.noMatchesFound")

              popup.pack(true, true)

              if (list.selectedIndex == -1) {
                list.selectedIndex = 0
              }
            }, EDT_EXECUTOR)
            .exceptionally {
              list.setPaintBusy(false)
              list.emptyText.clear()
              list.emptyText.appendText("Can't load the list", SimpleTextAttributes.ERROR_ATTRIBUTES)
              list.emptyText.appendSecondaryText(it.message.orEmpty(), SimpleTextAttributes.ERROR_ATTRIBUTES, null)
              throw it
            }

          Disposer.register(popup, Disposable {
            progressIndicator.cancel()
            loadingFuture.cancel(true)
          })
        }

        override fun onClosed(event: LightweightWindowEvent) {
          if (!loadingFuture.isDone || loadingFuture.isCancelled || loadingFuture.isCompletedExceptionally) {
            result.cancel(true)
            return
          }

          val selected = listModel.items.filter { it.selected }.map { it.value }
          result.complete(CollectionDelta(originalSelection, selected))
        }
      })
      .createPopup()
      .showUnderneathOf(parentComponent)
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
      progressManager.run(object : Task.Backgroundable(project, "Adjusting List of ${StringUtil.pluralize(entityName).capitalize()}...",
                                                       true) {
        override fun run(indicator: ProgressIndicator) {
          adjuster(delta, indicator)
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, "Failed to adjust list of ${StringUtil.pluralize(entityName)}", error)
        }

        override fun onFinished() {
          busyStateTracker.release(pullRequest)
          dataLoader.findDataProvider(pullRequest)?.reloadDetails()
          listLoader.outdated = true
        }
      })
    }
  }

  private data class SelectableWrapper<T>(val value: T, var selected: Boolean = false)

  private sealed class SelectionListCellRenderer<T> : ListCellRenderer<SelectableWrapper<T>>, BorderLayoutPanel() {

    private val mainLabel = JLabel()
    private val checkIconLabel = JLabel()

    init {
      checkIconLabel.iconTextGap = JBUI.scale(UIUtil.DEFAULT_VGAP)
      checkIconLabel.border = JBUI.Borders.empty(0, 4)

      addToLeft(checkIconLabel)
      addToCenter(mainLabel)

      border = JBUI.Borders.empty(4, 0)
    }

    override fun getListCellRendererComponent(list: JList<out SelectableWrapper<T>>,
                                              value: SelectableWrapper<T>,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      foreground = UIUtil.getListForeground(isSelected, true)
      background = UIUtil.getListBackground(isSelected, true)

      mainLabel.foreground = foreground
      mainLabel.font = font

      mainLabel.text = getText(value.value)
      mainLabel.icon = getIcon(value.value)

      val icon = LafIconLookup.getIcon("checkmark", isSelected, false)
      checkIconLabel.icon = if (value.selected) icon else EmptyIcon.create(icon)

      return this
    }

    abstract fun getText(value: T): String
    abstract fun getIcon(value: T): Icon

    class Users(private val iconsProvider: CachingGithubAvatarIconsProvider)
      : SelectionListCellRenderer<GithubUser>() {

      override fun getText(value: GithubUser) = value.login
      override fun getIcon(value: GithubUser) = iconsProvider.getIcon(value.avatarUrl)

    }

    class Labels : SelectionListCellRenderer<GithubIssueLabel>() {

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