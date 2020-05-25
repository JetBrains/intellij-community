// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRSubmittableTextField
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStateModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStatePanel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.*
import org.jetbrains.plugins.github.ui.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal class GHPREditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is GHPRVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): GHPRFileEditor {
    file as GHPRVirtualFile

    return GHPRFileEditor(file.presentableName) {
      createEditorContentComponentContainer(project, file.dataContext, file.pullRequest)
    }
  }

  private fun createEditorContentComponentContainer(project: Project, dataContext: GHPRDataContext,
                                                    pullRequest: GHPullRequestShort): ComponentContainer {
    val disposable = Disposer.newDisposable()

    val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

    val detailsModel = SingleValueModel(dataProvider.detailsData.loadedDetails ?: pullRequest)
    val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(dataProvider.reviewData, disposable)

    val loader = dataProvider.acquireTimelineLoader(disposable)
    val errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) {
      loader.reset()
    }
    val errorModel = GHHandledErrorPanelModel(GithubBundle.message("pull.request.timeline.cannot.load"),
                                              errorHandler).apply {
      error = loader.error
    }
    loader.addErrorChangeListener(disposable) {
      errorModel.error = loader.error
    }

    dataProvider.detailsData.loadDetails(disposable) {
      it.handleOnEdt(disposable) { pr, _ ->
        if (pr != null) detailsModel.value = pr
      }
    }

    val avatarIconsProviderFactory = CachingGithubAvatarIconsProvider.Factory(CachingGithubUserAvatarLoader.getInstance(),
                                                                              GithubImageResizer.getInstance(),
                                                                              dataContext.requestExecutor)

    val mainPanel = Wrapper().also {
      DataManager.registerDataProvider(it, DataProvider { dataId ->
        if (GHPRActionKeys.ACTION_DATA_CONTEXT.`is`(dataId))
          GHPRFixedActionDataContext(dataContext, dataProvider, avatarIconsProviderFactory) {
            detailsModel.value
          }
        else null
      })
    }

    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, mainPanel)

    val header = GHPRHeaderPanel(detailsModel, avatarIconsProvider)

    val timelineModel = GHPRTimelineMergingModel()
    loader.addDataListener(disposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = loader.loadedData
        timelineModel.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onAllDataRemoved() = timelineModel.removeAll()

      override fun onDataUpdated(idx: Int) = throw UnsupportedOperationException("Inplace timeline event update is not supported")
    })
    val timeline = GHPRTimelineComponent(timelineModel,
                                         createItemComponentFactory(project, dataProvider.reviewData, reviewThreadsModelsProvider,
                                                                    avatarIconsProvider, dataContext.securityService.currentUser)).apply {
      border = JBUI.Borders.empty(16, 0)
    }
    val loadingIcon = JLabel(AnimatedIcon.Default()).apply {
      border = JBUI.Borders.empty(8, 0)
      isVisible = loader.loading
    }
    loader.addLoadingStateChangeListener(disposable) {
      loadingIcon.isVisible = loader.loading
    }

    val errorPanel = GHHtmlErrorPanel.create(errorModel)
    val timelinePanel = ScrollablePanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(24, 20)

      val maxWidth = (GithubUIUtil.getFontEM(this) * 42).toInt()

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fill()
                           .flowY(),
                         AC().size(":$maxWidth:$maxWidth").gap("push"))

      add(header)
      add(timeline, CC().growX().minWidth(""))
      add(errorPanel, CC().hideMode(2).alignX("center"))
      add(loadingIcon, CC().hideMode(2).alignX("center"))

      if (dataContext.securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
        val commentField = createCommentField(dataProvider.commentsData,
                                              avatarIconsProvider,
                                              dataContext.securityService.currentUser)
        add(commentField, CC().growX())
      }
    }


    val scrollPane = ScrollPaneFactory.createScrollPane(timelinePanel, true).apply {
      background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      viewport.isOpaque = false
      verticalScrollBar.model.addChangeListener(object : ChangeListener {
        private var firstScroll = true

        override fun stateChanged(e: ChangeEvent) {
          if (firstScroll && verticalScrollBar.value > 0) firstScroll = false
          if (!firstScroll) {
            if (loader.canLoadMore()) {
              loader.loadMore()
            }
          }
        }
      })
    }.also {
      GithubUIUtil.addUIUpdateListener(it) {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }

    loader.addDataListener(disposable, object : GHListLoader.ListDataListener {
      override fun onAllDataRemoved() {
        if (scrollPane.isShowing) loader.loadMore()
      }
    })

    val stateModel = GHPRStateModelImpl(project, dataProvider, detailsModel.value, disposable)
    val statePanel = GHPRStatePanel(dataContext.securityService, stateModel).apply {
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                  JBUI.Borders.empty(8))
    }
    detailsModel.addAndInvokeValueChangedListener {
      statePanel.select(detailsModel.value.state, true)
    }

    val contentPanel = JBUI.Panels.simplePanel(scrollPane).addToBottom(statePanel).andTransparent()

    mainPanel.setContent(contentPanel)

    val actionManager = ActionManager.getInstance()
    (actionManager.getAction("Github.PullRequest.Timeline.Update") as RefreshAction).registerCustomShortcutSet(scrollPane, disposable)
    val actionGroup = actionManager.getAction("Github.PullRequest.Timeline.Popup") as ActionGroup
    PopupHandler.installPopupHandler(scrollPane, actionGroup, ActionPlaces.UNKNOWN, actionManager)

    loader.loadMore()

    return object : ComponentContainer {
      override fun getComponent() = mainPanel
      override fun getPreferredFocusableComponent() = contentPanel
      override fun dispose() = Disposer.dispose(disposable)
    }
  }

  private fun createCommentField(commentService: GHPRCommentsDataProvider,
                                 avatarIconsProvider: GHAvatarIconsProvider,
                                 currentUser: GHUser): JComponent {
    val model = GHPRSubmittableTextField.Model {
      commentService.addComment(EmptyProgressIndicator(), it)
    }
    return GHPRSubmittableTextField.create(model, avatarIconsProvider, currentUser)
  }

  private fun createItemComponentFactory(project: Project,
                                         reviewDataProvider: GHPRReviewDataProvider,
                                         reviewThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                         avatarIconsProvider: GHAvatarIconsProvider,
                                         currentUser: GHUser)
    : GHPRTimelineItemComponentFactory {

    val diffFactory = GHPRReviewThreadDiffComponentFactory(FileTypeRegistry.getInstance(), project, EditorFactory.getInstance())
    val eventsFactory = GHPRTimelineEventComponentFactoryImpl(avatarIconsProvider)
    return GHPRTimelineItemComponentFactory(reviewDataProvider, avatarIconsProvider, reviewThreadsModelsProvider, diffFactory,
                                            eventsFactory,
                                            currentUser)
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}