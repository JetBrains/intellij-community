// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GHPRFixedActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRCommentsUIUtil
import org.jetbrains.plugins.github.pullrequest.data.GHPRTimelineLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStatePanel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.*
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.event.AdjustmentListener
import javax.swing.BorderFactory
import javax.swing.JComponent

internal class GHPREditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (file !is GHPRVirtualFile) return false
    val context = file.context
    return context.pullRequest != null && context.pullRequestDataProvider != null && context.pullRequestDetails != null
  }

  override fun createEditor(project: Project, file: VirtualFile): GHPRFileEditor {
    file as GHPRVirtualFile
    val context = file.context

    return GHPRFileEditor(file.presentableName) {
      createEditorContentComponentContainer(project, context)
    }
  }

  private fun createEditorContentComponentContainer(project: Project, context: GHPRActionDataContext): ComponentContainer {
    val disposable = Disposer.newDisposable()

    val dataProvider = context.pullRequestDataProvider!!

    val detailsModel = SingleValueModel(context.pullRequestDetails!!)
    val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(dataProvider, disposable)

    val loader: GHPRTimelineLoader = dataProvider.acquireTimelineLoader(disposable)

    fun handleDetails() {
      dataProvider.detailsRequest.handleOnEdt(disposable) { pr, _ ->
        if (pr != null) detailsModel.value = pr
      }
    }
    dataProvider.addRequestsChangesListener(disposable, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun detailsRequestChanged() = handleDetails()
    })
    handleDetails()

    val mainPanel = Wrapper().also {
      DataManager.registerDataProvider(it, DataProvider { dataId ->
        if (GithubPullRequestKeys.ACTION_DATA_CONTEXT.`is`(dataId))
          GHPRFixedActionDataContext(context, dataProvider)
        else null
      })
    }

    val avatarIconsProvider = context.avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, mainPanel)

    val header = GHPRHeaderPanel(detailsModel, avatarIconsProvider)
    val reviewService = dataProvider.let { GHPRReviewServiceAdapter.create(context.reviewService, it) }
    val timeline = GHPRTimelineComponent(loader.listModel,
                                         createItemComponentFactory(project, reviewService, reviewThreadsModelsProvider,
                                                                    avatarIconsProvider, context.currentUser)).apply {
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
                             .flowY()).apply {
          columnConstraints = AC().fill().size("0:$maxWidth:$maxWidth")
        }

        emptyText.clear()

        add(header, CC().width("0:$maxWidth:$maxWidth"))
        add(timeline, CC().width("0:$maxWidth:$maxWidth"))
        add(loadingIcon, CC().width("0:$maxWidth:$maxWidth").hideMode(2).alignX("center"))

        with(context.commentService) {
          if (canComment()) {
            val commentServiceAdapter = GHPRCommentServiceAdapter.create(this, dataProvider)
            add(createCommentField(project, commentServiceAdapter, avatarIconsProvider, context.currentUser),
                CC().width("0:$maxWidth:$maxWidth"))
          }
        }
      }

      override fun getEmptyText() = timeline.emptyText

      override fun dispose() {}
    }

    val loaderPanel = object : GHListLoaderPanel<GHPRTimelineLoader>(loader, timelinePanel, true) {
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

    val statePanel = GHPRStatePanel.create(project, detailsModel,
                                           dataProvider,
                                           context.securityService,
                                           context.busyStateTracker,
                                           context.stateService,
                                           disposable)

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
    (actionManager.getAction("Github.PullRequest.Timeline.Update") as RefreshAction).registerShortcutOn(mainPanel)
    val actionGroup = actionManager.getAction("Github.PullRequest.Timeline.Popup") as ActionGroup
    PopupHandler.installPopupHandler(timelinePanel, actionGroup, ActionPlaces.UNKNOWN, actionManager)

    return object : ComponentContainer {
      override fun getComponent() = mainPanel
      override fun getPreferredFocusableComponent() = contentPanel
      override fun dispose() = Disposer.dispose(disposable)
    }
  }

  private fun createCommentField(project: Project,
                                 commentService: GHPRCommentServiceAdapter,
                                 avatarIconsProvider: GHAvatarIconsProvider,
                                 currentUser: GHUser): JComponent {
    return GHPRCommentsUIUtil.createCommentField(project, avatarIconsProvider, currentUser) {
      commentService.addComment(EmptyProgressIndicator(), it)
    }
  }

  private fun createItemComponentFactory(project: Project,
                                         reviewService: GHPRReviewServiceAdapter,
                                         reviewThreadsModelsProvider: GHPRReviewsThreadsModelsProvider,
                                         avatarIconsProvider: GHAvatarIconsProvider,
                                         currentUser: GHUser)
    : GHPRTimelineItemComponentFactory {

    val diffFactory = GHPRReviewThreadDiffComponentFactory(FileTypeRegistry.getInstance(), project, EditorFactory.getInstance())
    val eventsFactory = GHPRTimelineEventComponentFactoryImpl(avatarIconsProvider)
    return GHPRTimelineItemComponentFactory(project, reviewService, avatarIconsProvider, reviewThreadsModelsProvider, diffFactory,
                                            eventsFactory,
                                            currentUser)
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}