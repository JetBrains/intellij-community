// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
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
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRSubmittableTextField
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRTimelineLoader
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentServiceAdapter
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStatePanel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.*
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.event.AdjustmentListener
import javax.swing.BorderFactory
import javax.swing.JComponent

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

    val dataProvider = dataContext.dataLoader.getDataProvider(pullRequest, disposable)

    val detailsModel = SingleValueModel(pullRequest)
    val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(dataProvider.reviewData, disposable)

    val loader: GHPRTimelineLoader = dataProvider.acquireTimelineLoader(disposable)

    fun handleDetails() {
      dataProvider.detailsRequest.handleOnEdt(disposable) { pr, _ ->
        detailsModel.value = pr
      }
    }
    dataProvider.addRequestsChangesListener(disposable, object : GHPRDataProvider.RequestsChangedListener {
      override fun detailsRequestChanged() = handleDetails()
    })
    handleDetails()

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
    val timeline = GHPRTimelineComponent(loader.listModel,
                                         createItemComponentFactory(project, dataProvider.reviewData, reviewThreadsModelsProvider,
                                                                    avatarIconsProvider, dataContext.securityService.currentUser)).apply {
      border = JBUI.Borders.empty(16, 0)
    }
    val loadingIcon = AsyncProcessIcon("Loading").apply {
      border = JBUI.Borders.empty(8, 0)
      isVisible = false
    }

    val timelinePanel = object : ScrollablePanel(), ComponentWithEmptyText, Disposable {
      init {
        isOpaque = false
        border = JBUI.Borders.empty(24, 20)

        val maxWidth = (GithubUIUtil.getFontEM(this) * 42).toInt()

        layout = MigLayout(LC().gridGap("0", "0")
                             .insets("0", "0", "0", "0")
                             .fillX()
                             .flowY(),
                           AC().size(":$maxWidth:$maxWidth").gap("push"))

        emptyText.clear()

        add(header)
        add(timeline, CC().growX().minWidth(""))
        add(loadingIcon, CC().hideMode(2).alignX("center"))

        with(dataContext.commentService) {
          if (canComment()) {
            val commentServiceAdapter = GHPRCommentServiceAdapter.create(this, dataProvider)
            add(createCommentField(commentServiceAdapter, avatarIconsProvider, dataContext.securityService.currentUser), CC().growX())
          }
        }
      }

      override fun getEmptyText() = timeline.emptyText

      override fun dispose() {}
    }

    val loaderPanel = object : GHListLoaderPanel<GHPRTimelineLoader>(loader, timelinePanel, true) {
      init {
        errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) {
          loader.reset()
        }
      }

      override val loadingText
        get() = ""

      override fun createCenterPanel(content: JComponent) = Wrapper(content)

      override fun setLoading(isLoading: Boolean) {
        loadingIcon.isVisible = isLoading
      }

      override fun updateUI() {
        super.updateUI()
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }
    Disposer.register(disposable, loaderPanel)
    Disposer.register(loaderPanel, timelinePanel)
    Disposer.register(timelinePanel, loadingIcon)

    val statePanel = GHPRStatePanel(project, dataProvider, dataContext.securityService, dataContext.stateService, detailsModel, disposable)

    val contentPanel = JBUI.Panels.simplePanel(loaderPanel).addToBottom(statePanel).andTransparent()

    val verticalScrollBar = loaderPanel.scrollPane.verticalScrollBar
    verticalScrollBar.addAdjustmentListener(AdjustmentListener {

      if (verticalScrollBar.maximum - verticalScrollBar.visibleAmount >= 1) {
        statePanel.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                               JBUI.Borders.empty(8))
      }
      else {
        statePanel.border = JBUI.Borders.empty(8)
      }

    })

    mainPanel.setContent(contentPanel)

    val actionManager = ActionManager.getInstance()
    (actionManager.getAction("Github.PullRequest.Timeline.Update") as RefreshAction).registerCustomShortcutSet(mainPanel, disposable)
    val actionGroup = actionManager.getAction("Github.PullRequest.Timeline.Popup") as ActionGroup
    PopupHandler.installPopupHandler(timelinePanel, actionGroup, ActionPlaces.UNKNOWN, actionManager)

    loader.loadMore()

    return object : ComponentContainer {
      override fun getComponent() = mainPanel
      override fun getPreferredFocusableComponent() = contentPanel
      override fun dispose() = Disposer.dispose(disposable)
    }
  }

  private fun createCommentField(commentService: GHPRCommentServiceAdapter,
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