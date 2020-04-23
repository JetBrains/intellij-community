// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.DelayedTaskScheduler
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.FlowLayout
import javax.swing.*
import kotlin.properties.Delegates.observable

internal class GHPRStatePanel(private val project: Project,
                              private val dataProvider: GHPRDataProvider,
                              private val securityService: GHPRSecurityService,
                              private val stateService: GHPRStateService,
                              private val detailsModel: SingleValueModel<GHPullRequestShort>,
                              parentDisposable: Disposable)
  : CardLayoutPanel<GHPullRequestState, GHPRStatePanel.StateUI, JComponent>() {

  private var currentState by observable(detailsModel.value.state) { _, oldValue, newValue ->
    resetValue(oldValue)
    select(newValue, true)
  }
  private var openComponentDisposable = Disposer.newDisposable()

  init {
    Disposer.register(parentDisposable, this)
    detailsModel.addAndInvokeValueChangedListener(this) {
      currentState = detailsModel.value.state
    }
  }

  override fun prepare(key: GHPullRequestState): StateUI {
    return when (key) {
      GHPullRequestState.MERGED -> StateUI.Merged
      GHPullRequestState.CLOSED -> StateUI.Closed(dataProvider, securityService, detailsModel.value.viewerDidAuthor, stateService)
      GHPullRequestState.OPEN -> StateUI.Open(project, dataProvider, securityService, stateService,
                                              detailsModel, openComponentDisposable)
    }
  }

  override fun dispose(key: GHPullRequestState?) {
    if (key == GHPullRequestState.OPEN) {
      Disposer.dispose(openComponentDisposable)
      openComponentDisposable = Disposer.newDisposable()
    }
  }

  override fun create(ui: StateUI) = ui.createComponent()

  internal sealed class StateUI {

    companion object {
      const val STATUSES_GAP = 4
    }

    fun createComponent(): JComponent {
      val statusComponent = createStatusComponent()

      val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
      val errorComponent = HtmlEditorPane().apply {
        foreground = SimpleTextAttributes.ERROR_ATTRIBUTES.fgColor
      }
      val actionsPanel = BorderLayoutPanel().andTransparent()
        .addToLeft(buttonsPanel).addToCenter(errorComponent)
      for (button in createButtons { errorComponent.setBody(it) }) {
        buttonsPanel.add(button)
      }

      return NonOpaquePanel(VerticalLayout(4)).apply {
        border = JBUI.Borders.emptyLeft(4)

        add(statusComponent, VerticalLayout.FILL_HORIZONTAL)
        add(actionsPanel, VerticalLayout.FILL_HORIZONTAL)
      }
    }

    abstract fun createStatusComponent(): JComponent

    abstract fun createButtons(errorHandler: (String) -> Unit): List<JComponent>

    object Merged : StateUI() {

      override fun createStatusComponent() = JLabel(GithubBundle.message("pull.request.state.merged"), GithubIcons.PullRequestMerged,
                                                    SwingConstants.LEFT)

      override fun createButtons(errorHandler: (String) -> Unit) = emptyList<JComponent>()
    }

    class Closed(private val dataProvider: GHPRDataProvider,
                 securityService: GHPRSecurityService,
                 viewerIsAuthor: Boolean,
                 private val stateService: GHPRStateService) : StateUI() {

      private val canReopen = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerIsAuthor

      override fun createStatusComponent(): JComponent {
        val stateLabel = JLabel(GithubBundle.message("pull.request.state.closed"), GithubIcons.PullRequestClosed, SwingConstants.LEFT)
        return if (canReopen) stateLabel
        else {
          val accessDeniedLabel = JLabel().apply {
            icon = AllIcons.RunConfigurations.TestError
            text = GithubBundle.message("pull.request.repo.access.required")
          }
          JPanel(VerticalLayout(STATUSES_GAP)).apply {
            add(stateLabel, VerticalLayout.FILL_HORIZONTAL)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
          }
        }
      }

      override fun createButtons(errorHandler: (String) -> Unit): List<JComponent> {
        return if (canReopen) {
          val action = GHPRReopenAction(SingleValueModel(false), errorHandler,
                                        stateService, dataProvider.id)
          listOf(JButton(action))
        }
        else emptyList()
      }
    }

    class Open(private val project: Project,
               private val dataProvider: GHPRDataProvider,
               securityService: GHPRSecurityService,
               private val stateService: GHPRStateService,
               private val detailsModel: SingleValueModel<GHPullRequestShort>,
               parentDisposable: Disposable) : StateUI() {

      private val canClose =
        securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || detailsModel.value.viewerDidAuthor
      private val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
      private val mergeForbidden = securityService.isMergeForbiddenForProject()

      private val canCommitMerge = securityService.isMergeAllowed()
      private val canSquashMerge = securityService.isSquashMergeAllowed()
      private val canRebaseMerge = securityService.isRebaseMergeAllowed()

      private val mergeabilityModel = SingleValueModel<GHPRMergeabilityState?>(null)
      private val mergeabilityPoller = DelayedTaskScheduler(3, parentDisposable) {
        dataProvider.reloadMergeabilityState()
      }

      init {
        dataProvider.addRequestsChangesListener(parentDisposable, object : GHPRDataProvider.RequestsChangedListener {
          override fun mergeabilityStateRequestChanged() {
            loadMergeability()
          }
        })
        loadMergeability()

        mergeabilityModel.addValueChangedListener {
          val state = mergeabilityModel.value
          if (state != null && state.hasConflicts == null) {
            mergeabilityPoller.start()
          }
          else mergeabilityPoller.stop()
        }
      }

      private fun loadMergeability() {
        dataProvider.mergeabilityStateRequest.successOnEdt {
          mergeabilityModel.value = it
        }
      }

      override fun createStatusComponent(): JComponent {
        val panel = Wrapper()
        LoadingController(mergeabilityModel, panel, ::createNotLoadedComponent, ::createLoadedComponent)
        return panel
      }

      private fun createNotLoadedComponent(): JComponent {
        val stateLabel = JLabel(GithubBundle.message("pull.request.loading.status"), AllIcons.RunConfigurations.TestNotRan,
                                SwingConstants.LEFT)
        val accessDeniedLabel = createAccessDeniedLabel()
        return if (accessDeniedLabel == null) stateLabel
        else {
          JPanel(VerticalLayout(STATUSES_GAP)).apply {
            add(stateLabel, VerticalLayout.FILL_HORIZONTAL)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
          }
        }
      }

      private fun createLoadedComponent(mergeabilityModel: SingleValueModel<GHPRMergeabilityState>): JComponent {
        val statusChecks = GHPRStatusChecksComponent.create(mergeabilityModel)

        val conflictsLabel = JLabel()
        ConflictsController(mergeabilityModel, conflictsLabel)

        val requiredReviewsLabel = JLabel()
        RequiredReviewsController(mergeabilityModel, requiredReviewsLabel)

        val restrictionsLabel = JLabel()
        RestrictionsController(mergeabilityModel, restrictionsLabel)

        val accessDeniedLabel = createAccessDeniedLabel()
        return JPanel(VerticalLayout(STATUSES_GAP)).apply {
          add(statusChecks, VerticalLayout.FILL_HORIZONTAL)
          add(requiredReviewsLabel, VerticalLayout.FILL_HORIZONTAL)
          add(conflictsLabel, VerticalLayout.FILL_HORIZONTAL)
          add(restrictionsLabel, VerticalLayout.FILL_HORIZONTAL)
          if (accessDeniedLabel != null)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
        }
      }

      private fun createAccessDeniedLabel(): JComponent? {
        return when {
          !canClose -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = GithubBundle.message("pull.request.repo.access.required")
            }
          }
          !canMerge -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = GithubBundle.message("pull.request.repo.write.access.required")
            }
          }
          mergeForbidden -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = GithubBundle.message("pull.request.merge.disabled")
            }
          }
          else -> null
        }
      }

      override fun createButtons(errorHandler: (String) -> Unit): List<JComponent> {
        val list = mutableListOf<JComponent>()
        val busyStateModel = SingleValueModel(false)
        if (canMerge && !mergeForbidden) {
          val allowedActions = mutableListOf<Action>()
          if (canCommitMerge)
            allowedActions.add(GHPRCommitMergeAction(busyStateModel, errorHandler,
                                                     detailsModel, mergeabilityModel, project, stateService))
          if (canRebaseMerge)
            allowedActions.add(GHPRRebaseMergeAction(busyStateModel, errorHandler,
                                                     mergeabilityModel, stateService))
          if (canSquashMerge)
            allowedActions.add(GHPRSquashMergeAction(busyStateModel, errorHandler,
                                                     mergeabilityModel, project, stateService, dataProvider))

          val action = allowedActions.firstOrNull()
          val actions = if (allowedActions.size > 1) Array(allowedActions.size - 1) { allowedActions[it + 1] } else emptyArray()

          val mergeButton = JBOptionButton(action, actions)
          list.add(mergeButton)
        }

        if (canClose) {
          val action = GHPRCloseAction(SingleValueModel(false), errorHandler,
                                       stateService, dataProvider.id)
          list.add(JButton(action))
        }
        return list
      }

      private class LoadingController(private val loadingMergeabilityModel: SingleValueModel<GHPRMergeabilityState?>,
                                      private val panel: Wrapper,
                                      private val notLoadedContentFactory: () -> JComponent,
                                      private val loadedContentFactory: (mergeabilityModel: SingleValueModel<GHPRMergeabilityState>) -> JComponent) {

        private var loadedMergeabilityModel: SingleValueModel<GHPRMergeabilityState>? = null

        init {
          loadingMergeabilityModel.addAndInvokeValueChangedListener(this::update)
        }

        private fun update() {
          val mergeability = loadingMergeabilityModel.value
          if (mergeability == null) {
            loadedMergeabilityModel = null
            panel.setContent(notLoadedContentFactory())
          }
          else {
            var mergeabilityModel = loadedMergeabilityModel
            if (mergeabilityModel == null) {
              mergeabilityModel = SingleValueModel(mergeability)
              panel.setContent(loadedContentFactory(mergeabilityModel))
              panel.revalidate()
              loadedMergeabilityModel = mergeabilityModel
            }
            else {
              mergeabilityModel.value = mergeability
            }
          }
        }
      }

      private class ConflictsController(private val mergeabilityModel: SingleValueModel<GHPRMergeabilityState>,
                                        private val label: JLabel) {

        init {
          mergeabilityModel.addAndInvokeValueChangedListener(::update)
        }

        private fun update() {
          when (mergeabilityModel.value.hasConflicts) {
            false -> {
              label.icon = AllIcons.RunConfigurations.TestPassed
              label.text = GithubBundle.message("pull.request.conflicts.none")
            }
            true -> {
              label.icon = AllIcons.RunConfigurations.TestError
              label.text = GithubBundle.message("pull.request.conflicts.must.be.resolved")
            }
            null -> {
              label.icon = AllIcons.RunConfigurations.TestNotRan
              label.text = GithubBundle.message("pull.request.conflicts.checking")
            }
          }
        }
      }

      private class RequiredReviewsController(private val mergeabilityModel: SingleValueModel<GHPRMergeabilityState>,
                                              private val label: JLabel) {

        init {
          mergeabilityModel.addAndInvokeValueChangedListener(::update)
        }

        private fun update() {
          val requiredApprovingReviewsCount = mergeabilityModel.value.requiredApprovingReviewsCount
          label.isVisible = requiredApprovingReviewsCount > 0
          with(label) {
            icon = AllIcons.RunConfigurations.TestError
            text = GithubBundle.message("pull.request.reviewers.required", requiredApprovingReviewsCount)
          }
        }
      }

      private class RestrictionsController(private val mergeabilityModel: SingleValueModel<GHPRMergeabilityState>,
                                           private val label: JLabel) {

        init {
          mergeabilityModel.addAndInvokeValueChangedListener(::update)
        }

        private fun update() {
          with(label) {
            isVisible = mergeabilityModel.value.isRestricted
            icon = AllIcons.RunConfigurations.TestError
            text = GithubBundle.message("pull.request.not.authorized.to.merge")
          }
        }
      }
    }
  }
}