// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import icons.DvcsImplIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel

internal object GHPRBranchesPanel {

  fun create(model: GHPRBranchesModel): JComponent {
    val from = createLabel()
    val to = createLabel()
    val branchActionsToolbar = BranchActionsToolbar()

    Controller(model, from, to, branchActionsToolbar)

    return NonOpaquePanel().apply {
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

    add(to, CC().minWidth("30"))
      add(JLabel(" ${UIUtil.leftArrow()} ").apply {
        foreground = CurrentBranchComponent.TEXT_COLOR
        border = JBUI.Borders.empty(0, 5)
      })
    add(from, CC().minWidth("30"))
      add(branchActionsToolbar)
    }
  }

  private fun createLabel() = JBLabel(CollaborationToolsIcons.Review.Branch).also {
    CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
      foreground = CurrentBranchComponent.TEXT_COLOR
      background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
    }
  }.andOpaque()

  private class Controller(private val model: GHPRBranchesModel,
                           private val from: JBLabel,
                           private val to: JBLabel,
                           private val branchActionsToolbar: BranchActionsToolbar) {

    val branchesTooltipFactory = GHPRBranchesTooltipFactory()

    init {
      branchesTooltipFactory.installTooltip(from)

      model.addAndInvokeChangeListener {
        updateBranchActionsToolbar()
        updateBranchLabels()
      }
    }

    private fun updateBranchLabels() {
      val localRepository = model.localRepository
      val localBranch = with(localRepository.branches) { model.localBranch?.run(::findLocalBranch) }
      val remoteBranch = localBranch?.findTrackedBranch(localRepository)
      val currentBranchCheckedOut = localRepository.currentBranchName == localBranch?.name

      to.text = model.baseBranch
      from.text = model.headBranch
      from.icon = when {
        currentBranchCheckedOut -> DvcsImplIcons.CurrentBranchFavoriteLabel
        localBranch != null -> GithubIcons.LocalBranch
        else -> CollaborationToolsIcons.Review.Branch
      }

      branchesTooltipFactory.apply {
        isOnCurrentBranch = currentBranchCheckedOut
        prBranchName = model.headBranch
        localBranchName = localBranch?.name
        remoteBranchName = remoteBranch?.name
      }
    }

    private fun updateBranchActionsToolbar() {
      val prRemote = model.prRemote
      if (prRemote == null) {
        branchActionsToolbar.showCheckoutAction()
        return
      }

      val localBranch = model.localBranch

      val updateActionExist = localBranch != null
      val multipleActionsExist = updateActionExist && model.localRepository.currentBranchName != localBranch

      with(branchActionsToolbar) {
        when {
          multipleActionsExist -> showMultiple()
          updateActionExist -> showUpdateAction()
          else -> showCheckoutAction()
        }
      }
    }
  }

  internal class BranchActionsToolbar : CardLayoutPanel<BranchActionsToolbar.State, BranchActionsToolbar.StateUi, JComponent>() {

    companion object {
      const val BRANCH_ACTIONS_TOOLBAR = "Github.PullRequest.Branch.Actions.Toolbar"
    }

    enum class State(private val text: String) {
      CHECKOUT_ACTION(VcsBundle.message("vcs.command.name.checkout")),
      UPDATE_ACTION(VcsBundle.message("vcs.command.name.update")),
      MULTIPLE_ACTIONS(GithubBundle.message("pull.request.branch.action.group.name"));

      override fun toString(): String = text
    }

    fun showCheckoutAction() {
      select(State.CHECKOUT_ACTION, true)
    }

    fun showUpdateAction() {
      select(State.UPDATE_ACTION, true)
    }

    fun showMultiple() {
      select(State.MULTIPLE_ACTIONS, true)
    }

    sealed class StateUi {

      abstract fun createUi(): JComponent

      object CheckoutActionUi : SingleActionUi("Github.PullRequest.Branch.Create", VcsBundle.message("vcs.command.name.checkout"))
      object UpdateActionUi : SingleActionUi("Github.PullRequest.Branch.Update", VcsBundle.message("vcs.command.name.update"))

      abstract class SingleActionUi(private val actionId: String, @NlsContexts.LinkLabel private val actionName: String) : StateUi() {
        override fun createUi(): JComponent =
          AnActionLink(actionId, BRANCH_ACTIONS_TOOLBAR)
            .apply {
              text = actionName
              border = JBUI.Borders.emptyLeft(8)
            }
      }

      object MultipleActionUi : StateUi() {

        private lateinit var dropDownLink: DropDownLink<State>

        private val invokeAction: JComponent.(String) -> Unit = { actionId ->
          val action = ActionManager.getInstance().getAction(actionId)
          ActionUtil.invokeAction(action, this, BRANCH_ACTIONS_TOOLBAR, null, null)
        }

        override fun createUi(): JComponent {
          dropDownLink = DropDownLink(State.MULTIPLE_ACTIONS, listOf(State.CHECKOUT_ACTION, State.UPDATE_ACTION), Consumer { state ->
            when (state) {
              State.CHECKOUT_ACTION -> dropDownLink.invokeAction("Github.PullRequest.Branch.Create")
              State.UPDATE_ACTION -> dropDownLink.invokeAction("Github.PullRequest.Branch.Update")
              State.MULTIPLE_ACTIONS -> {}
            }
          })
            .apply { border = JBUI.Borders.emptyLeft(8) }
          return dropDownLink
        }
      }
    }

    override fun prepare(state: State): StateUi =
      when (state) {
        State.CHECKOUT_ACTION -> StateUi.CheckoutActionUi
        State.UPDATE_ACTION -> StateUi.UpdateActionUi
        State.MULTIPLE_ACTIONS -> StateUi.MultipleActionUi
      }

    override fun create(ui: StateUi): JComponent = ui.createUi()
  }

  private class GHPRBranchesTooltipFactory(var isOnCurrentBranch: Boolean = false,
                                           var prBranchName: String = "",
                                           var localBranchName: String? = null,
                                           var remoteBranchName: String? = null) {
    fun installTooltip(label: JBLabel) {
      label.addMouseMotionListener(object : MouseAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          showTooltip(e)
        }
      })
    }

    private fun showTooltip(e: MouseEvent) {
      val point = e.point
      if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e)
      }

      val tooltip = IdeTooltip(e.component, point, Wrapper(createTooltip())).setPreferredPosition(Balloon.Position.below)
      IdeTooltipManager.getInstance().show(tooltip, false)
    }

    private fun createTooltip(): GHPRBranchesTooltip =
      GHPRBranchesTooltip(arrayListOf<BranchTooltipDescriptor>().apply {
        if (isOnCurrentBranch) add(BranchTooltipDescriptor.head())
        if (localBranchName != null) add(BranchTooltipDescriptor.localBranch(localBranchName!!))
        if (remoteBranchName != null) {
          add(BranchTooltipDescriptor.remoteBranch(remoteBranchName!!))
        }
        else {
          add(BranchTooltipDescriptor.prBranch(prBranchName))
        }
      })
  }
}