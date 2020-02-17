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
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.FlowLayout
import javax.swing.*
import kotlin.properties.Delegates.observable

internal class GHPRStatePanel(private val project: Project,
                              private val dataProvider: GHPRDataProvider,
                              private val securityService: GHPRSecurityService,
                              private val stateService: GHPRStateService,
                     private val detailsModel: SingleValueModel<out GHPullRequestShort>,
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
      GHPullRequestState.OPEN -> {
        val fullDetailsModel = SingleValueModel<GHPullRequest?>(null)
        detailsModel.addAndInvokeValueChangedListener(openComponentDisposable) {
          val details = detailsModel.value
          if (details is GHPullRequest) fullDetailsModel.value = details
        }
        StateUI.Open(project, dataProvider, securityService, detailsModel.value.viewerDidAuthor, stateService, fullDetailsModel)
      }
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

      override fun createStatusComponent() = JLabel("Pull request is merged", GithubIcons.PullRequestMerged, SwingConstants.LEFT)

      override fun createButtons(errorHandler: (String) -> Unit) = emptyList<JComponent>()
    }

    class Closed(private val dataProvider: GHPRDataProvider,
                 securityService: GHPRSecurityService,
                 viewerIsAuthor: Boolean,
                 private val stateService: GHPRStateService) : StateUI() {

      private val canReopen = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerIsAuthor

      override fun createStatusComponent(): JComponent {
        val stateLabel = JLabel("Pull request is closed", GithubIcons.PullRequestClosed, SwingConstants.LEFT)
        return if (canReopen) stateLabel
        else {
          val accessDeniedLabel = JLabel().apply {
            icon = AllIcons.RunConfigurations.TestError
            text = "Repository access required to manage pull requests"
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
                                        stateService, dataProvider.number)
          listOf(JButton(action))
        }
        else emptyList()
      }
    }

    class Open(private val project: Project,
               private val dataProvider: GHPRDataProvider,
               securityService: GHPRSecurityService,
               viewerIsAuthor: Boolean,
               private val stateService: GHPRStateService,
               private val fullDetailsModel: SingleValueModel<GHPullRequest?>) : StateUI() {

      private val canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerIsAuthor
      private val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
      private val mergeForbidden = securityService.isMergeForbiddenForProject()

      private val canCommitMerge = securityService.isMergeAllowed()
      private val canSquashMerge = securityService.isSquashMergeAllowed()
      private val canRebaseMerge = securityService.isRebaseMergeAllowed()

      override fun createStatusComponent(): JComponent {
        val panel = Wrapper()
        LoadingController(fullDetailsModel, panel, ::createNotLoadedComponent, ::createLoadedComponent)
        return panel
      }

      private fun createNotLoadedComponent(): JComponent {
        val stateLabel = JLabel("Loading pull request status...", AllIcons.RunConfigurations.TestNotRan, SwingConstants.LEFT)
        val accessDeniedLabel = createAccessDeniedLabel()
        return if (accessDeniedLabel == null) stateLabel
        else {
          JPanel(VerticalLayout(STATUSES_GAP)).apply {
            add(stateLabel, VerticalLayout.FILL_HORIZONTAL)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
          }
        }
      }

      private fun createLoadedComponent(detailsModel: SingleValueModel<GHPullRequest>): JComponent {
        val conflictsLabel = JLabel()
        ConflictsController(detailsModel, conflictsLabel)

        val accessDeniedLabel = createAccessDeniedLabel()
        return if (accessDeniedLabel == null) conflictsLabel
        else {
          JPanel(VerticalLayout(STATUSES_GAP)).apply {
            add(conflictsLabel, VerticalLayout.FILL_HORIZONTAL)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
          }
        }
      }

      private fun createAccessDeniedLabel(): JComponent? {
        return when {
          !canClose -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = "Repository access required to manage pull requests"
            }
          }
          !canMerge -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = "Repository write access required to merge pull requests"
            }
          }
          mergeForbidden -> {
            JLabel().apply {
              icon = AllIcons.RunConfigurations.TestError
              text = "Merging is disabled for this project"
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
                                                     fullDetailsModel, project, stateService))
          if (canRebaseMerge)
            allowedActions.add(GHPRRebaseMergeAction(busyStateModel, errorHandler,
                                                     fullDetailsModel, stateService))
          if (canSquashMerge)
            allowedActions.add(GHPRSquashMergeAction(busyStateModel, errorHandler,
                                                     fullDetailsModel, project, stateService, dataProvider))

          val action = allowedActions.firstOrNull()
          val actions = if (allowedActions.size > 1) Array(allowedActions.size - 1) { allowedActions[it + 1] } else emptyArray()

          val mergeButton = JBOptionButton(action, actions)
          list.add(mergeButton)
        }

        if (canClose) {
          val action = GHPRCloseAction(SingleValueModel(false), errorHandler,
                                       stateService, dataProvider.number)
          list.add(JButton(action))
        }
        return list
      }

      private class LoadingController(private val detailsModel: SingleValueModel<GHPullRequest?>,
                                      private val panel: Wrapper,
                                      private val notLoadedContentFactory: () -> JComponent,
                                      private val loadedContentFactory: (detailsModel: SingleValueModel<GHPullRequest>) -> JComponent) {

        init {
          detailsModel.addAndInvokeValueChangedListener(this::update)
        }

        private fun update() {
          val details = detailsModel.value
          if (details == null) {
            panel.setContent(notLoadedContentFactory())
          }
          else {
            val notNullModel = SingleValueModel(details)
            detailsModel.addAndInvokeValueChangedListener {
              notNullModel.value = detailsModel.value ?: return@addAndInvokeValueChangedListener
            }
            panel.setContent(loadedContentFactory(notNullModel))
          }
        }
      }

      private class ConflictsController(private val detailsModel: SingleValueModel<GHPullRequest>,
                                        private val label: JLabel) {

        init {
          detailsModel.addAndInvokeValueChangedListener(::update)
        }

        private fun update() {
          when (detailsModel.value.mergeable) {
            GHPullRequestMergeableState.MERGEABLE -> {
              label.icon = AllIcons.RunConfigurations.TestPassed
              label.text = "Branch has no conflicts with base branch"
            }
            GHPullRequestMergeableState.CONFLICTING -> {
              label.icon = AllIcons.RunConfigurations.TestError
              label.text = "Branch has conflicts that must be resolved"
            }
            GHPullRequestMergeableState.UNKNOWN -> {
              label.icon = AllIcons.RunConfigurations.TestNotRan
              label.text = "Checking for ability to merge automatically..."
            }
          }
        }
      }
    }
  }
}