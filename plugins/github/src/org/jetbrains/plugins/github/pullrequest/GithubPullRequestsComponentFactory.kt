// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactoryImpl
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.ui.*
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent


internal class GithubPullRequestsComponentFactory(private val project: Project,
                                                  private val copyPasteManager: CopyPasteManager,
                                                  private val avatarLoader: CachingGithubUserAvatarLoader,
                                                  private val imageResizer: GithubImageResizer,
                                                  private val actionManager: ActionManager,
                                                  private val autoPopupController: AutoPopupController,
                                                  private val pullRequestUiSettings: GithubPullRequestsProjectUISettings,
                                                  private val fileEditorManager: FileEditorManager) {

  fun createComponent(dataContext: GHPullRequestsDataContext): JComponent {
    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(avatarLoader, imageResizer, dataContext.requestExecutor)
    return GithubPullRequestsComponent(dataContext, avatarIconsProviderFactory)
  }

  private inner class GithubPullRequestsComponent(private val dataContext: GHPullRequestsDataContext,
                                                  avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
    : OnePixelSplitter("Github.PullRequests.Component", 0.33f), Disposable, DataProvider {

    private val listSelectionHolder = GithubPullRequestsListSelectionHolderImpl()
    private val actionDataContext = GHPRActionDataContext(dataContext, listSelectionHolder, avatarIconsProviderFactory)

    private val uiDisposable: Disposable

    init {
      val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, dataContext.listModel)
      list.emptyText.clear()
      installPopup(list)
      installSelectionSaver(list)
      list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2 && ListUtil.isPointOnSelection(list, e.x, e.y)) {
            openTimelineForSelection(list)
            e.consume()
          }
        }
      })
      list.registerKeyboardAction({ openTimelineForSelection(list) }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                  JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

      val search = GithubPullRequestSearchPanel(project, autoPopupController, dataContext.searchHolder).apply {
        border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      }
      val loaderPanel = GHPRListLoaderPanel(dataContext.listLoader, dataContext.dataLoader, list, search)
      firstComponent = loaderPanel

      val diffCommentComponentFactory = GHPREditorReviewThreadComponentFactoryImpl(avatarIconsProviderFactory)
      val changes = GithubPullRequestChangesComponent(project, pullRequestUiSettings, diffCommentComponentFactory).apply {
        diffAction.registerCustomShortcutSet(this@GithubPullRequestsComponent, this@GithubPullRequestsComponent)
      }
      val details = GithubPullRequestDetailsComponent(project,
                                                      dataContext.dataLoader,
                                                      dataContext.securityService,
                                                      dataContext.busyStateTracker,
                                                      dataContext.metadataService,
                                                      dataContext.stateService,
                                                      avatarIconsProviderFactory)
      val preview = GithubPullRequestPreviewComponent(changes, details)

      listSelectionHolder.addSelectionChangeListener(preview) {
        preview.dataProvider = listSelectionHolder.selectionNumber?.let(dataContext.dataLoader::getDataProvider)
      }

      dataContext.dataLoader.addInvalidationListener(preview) {
        val selection = listSelectionHolder.selectionNumber
        if (selection != null && selection == it) {
          preview.dataProvider = dataContext.dataLoader.getDataProvider(selection)
        }
      }

      secondComponent = preview
      isFocusCycleRoot = true

      uiDisposable = Disposable {
        Disposer.dispose(list)
        Disposer.dispose(search)
        Disposer.dispose(loaderPanel)

        Disposer.dispose(preview)
        Disposer.dispose(changes)
        Disposer.dispose(details)
      }
    }

    private fun openTimelineForSelection(list: GithubPullRequestsList) {
      val pullRequest = list.selectedValue
      val file = GHPRVirtualFile(actionDataContext,
                                 pullRequest,
                                 dataContext.dataLoader.getDataProvider(pullRequest.number))
      fileEditorManager.openFile(file, true)
    }

    private fun installPopup(list: GithubPullRequestsList) {
      val popupHandler = object : PopupHandler() {
        override fun invokePopup(comp: java.awt.Component, x: Int, y: Int) {
          if (ListUtil.isPointOnSelection(list, x, y)) {
            val popupMenu = actionManager
              .createActionPopupMenu("GithubPullRequestListPopup",
                                     actionManager.getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup)
            popupMenu.setTargetComponent(list)
            popupMenu.component.show(comp, x, y)
          }
        }
      }
      list.addMouseListener(popupHandler)
    }

    private fun installSelectionSaver(list: GithubPullRequestsList) {
      var savedSelectionNumber: Long? = null

      list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
        if (!e.valueIsAdjusting) {
          val selectedIndex = list.selectedIndex
          if (selectedIndex >= 0 && selectedIndex < list.model.size) {
            listSelectionHolder.selectionNumber = list.model.getElementAt(selectedIndex).number
            savedSelectionNumber = null
          }
        }
      }

      list.model.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent) {
          if (e.type == ListDataEvent.INTERVAL_ADDED)
            (e.index0..e.index1).find { list.model.getElementAt(it).number == savedSelectionNumber }
              ?.run { ApplicationManager.getApplication().invokeLater { ScrollingUtil.selectItem(list, this) } }
        }

        override fun contentsChanged(e: ListDataEvent) {}
        override fun intervalRemoved(e: ListDataEvent) {
          if (e.type == ListDataEvent.INTERVAL_REMOVED) savedSelectionNumber = listSelectionHolder.selectionNumber
        }
      })
    }

    override fun getData(dataId: String): Any? {
      if (Disposer.isDisposed(this)) return null
      return when {
        GithubPullRequestKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> actionDataContext
        else -> null
      }
    }

    override fun dispose() {
      Disposer.dispose(uiDisposable)
      Disposer.dispose(dataContext)
    }
  }
}