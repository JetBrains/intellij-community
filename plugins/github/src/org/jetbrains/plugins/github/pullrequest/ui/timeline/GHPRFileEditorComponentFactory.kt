// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.UiNotifyConnector
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineFileEditor
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRSubmittableTextField
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStateModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStatePanel
import org.jetbrains.plugins.github.ui.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

internal class GHPRFileEditorComponentFactory(private val project: Project,
                                              private val editor: GHPRTimelineFileEditor,
                                              currentDetails: GHPullRequestShort) {

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(editor, it)
  }

  private val detailsModel = SingleValueModel(currentDetails)

  private val errorModel = GHHandledErrorPanelModel(GithubBundle.message("pull.request.timeline.cannot.load"),
                                                    GHLoadingErrorHandlerImpl(project,
                                                                              editor.securityService.account,
                                                                              editor.timelineLoader::reset))
  private val timelineModel = GHPRTimelineMergingModel()
  private val reviewThreadsModelsProvider = GHPRReviewsThreadsModelsProviderImpl(editor.reviewData, uiDisposable)

  private val stateModel = GHPRStateModelImpl(project, editor.stateData, editor.changesData, detailsModel, uiDisposable)

  init {
    editor.detailsData.loadDetails(uiDisposable) {
      it.handleOnEdt(uiDisposable) { pr, _ ->
        if (pr != null) detailsModel.value = pr
      }
    }

    editor.timelineLoader.addErrorChangeListener(uiDisposable) {
      errorModel.error = editor.timelineLoader.error
    }
    errorModel.error = editor.timelineLoader.error

    editor.timelineLoader.addDataListener(uiDisposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = editor.timelineLoader.loadedData
        timelineModel.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onAllDataRemoved() = timelineModel.removeAll()

      override fun onDataUpdated(idx: Int) = throw UnsupportedOperationException("Inplace timeline event update is not supported")
    })
    timelineModel.add(editor.timelineLoader.loadedData)
  }

  fun create(): JComponent {
    val mainPanel = Wrapper()

    val avatarIconsProvider = editor.avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, mainPanel)

    val header = GHPRHeaderPanel(detailsModel, avatarIconsProvider)

    val timeline = GHPRTimelineComponent(timelineModel,
                                         createItemComponentFactory(project, editor.reviewData, reviewThreadsModelsProvider,
                                                                    avatarIconsProvider, editor.securityService.currentUser)).apply {
      border = JBUI.Borders.empty(16, 0)
    }
    val errorPanel = GHHtmlErrorPanel.create(errorModel)

    val timelineLoader = editor.timelineLoader
    val loadingIcon = JLabel(AnimatedIcon.Default()).apply {
      border = JBUI.Borders.empty(8, 0)
      isVisible = timelineLoader.loading
    }
    timelineLoader.addLoadingStateChangeListener(uiDisposable) {
      loadingIcon.isVisible = timelineLoader.loading
    }

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

      if (editor.securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)) {
        val commentField = createCommentField(editor.commentsData,
                                              avatarIconsProvider,
                                              editor.securityService.currentUser)
        add(commentField, CC().growX())
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(timelinePanel, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      verticalScrollBar.model.addChangeListener(object : ChangeListener {
        private var firstScroll = true

        override fun stateChanged(e: ChangeEvent) {
          if (firstScroll && verticalScrollBar.value > 0) firstScroll = false
          if (!firstScroll) {
            if (timelineLoader.canLoadMore()) {
              timelineLoader.loadMore()
            }
          }
        }
      })
    }
    UiNotifyConnector.doWhenFirstShown(scrollPane) {
      timelineLoader.loadMore()
    }

    timelineLoader.addDataListener(uiDisposable, object : GHListLoader.ListDataListener {
      override fun onAllDataRemoved() {
        if (scrollPane.isShowing) timelineLoader.loadMore()
      }
    })

    val statePanel = GHPRStatePanel(editor.securityService, stateModel).apply {
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                  JBUI.Borders.empty(8))
    }
    detailsModel.addAndInvokeValueChangedListener {
      statePanel.select(detailsModel.value.state, true)
    }

    val contentPanel = JBUI.Panels.simplePanel(scrollPane).addToBottom(JBUI.Panels.simplePanel(statePanel)).andTransparent()
    mainPanel.setContent(contentPanel)

    val actionManager = ActionManager.getInstance()
    actionManager.getAction("Github.PullRequest.Timeline.Update").registerCustomShortcutSet(scrollPane, uiDisposable)
    val actionGroup = actionManager.getAction("Github.PullRequest.Timeline.Popup") as ActionGroup
    PopupHandler.installPopupHandler(scrollPane, actionGroup, ActionPlaces.UNKNOWN, actionManager)

    return mainPanel
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
}