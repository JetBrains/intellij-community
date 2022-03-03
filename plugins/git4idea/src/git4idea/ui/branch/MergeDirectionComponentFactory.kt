// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitPushUtil.findPushTarget
import git4idea.GitRemoteBranch
import git4idea.i18n.GitBundle
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.ComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class MergeDirectionComponentFactory<RepoMapping : GitRepositoryMappingData>(
  private val model: MergeDirectionModel<RepoMapping>,
  private val getBaseRepoPresentation: (MergeDirectionModel<RepoMapping>) -> @Nls String? = { it.baseRepo.toString() },
  private val getHeadRepoPresentation: (MergeDirectionModel<RepoMapping>) -> @Nls String? = { it.headRepo?.toString() },
) {
  fun create(): JComponent {
    val base = ActionLink("")
    base.addActionListener {
      chooseBaseBranch(base,
                       model.baseBranch,
                       model.baseRepo,
                       model::baseBranch::set)
    }

    val head = ActionLink("")
    head.addActionListener {
      val currentRepo = model.headRepo
      chooseHeadRepoAndBranch(head, currentRepo, model.headBranch, { repo, branch ->
        model.setHead(repo, branch)
        model.headSetByUser = true
      }, model.getKnownRepoMappings())
    }

    val changesWarningLabel = JLabel(AllIcons.General.Warning)

    model.addAndInvokeDirectionChangesListener {
      val headRepo = model.headRepo
      val headBranch = model.headBranch

      val baseText = getBaseRepoPresentation(model) ?: GitBundle.message("branch.direction.panel.select.link")
      base.text = baseText
      base.toolTipText = baseText
      val headText = getHeadRepoPresentation(model) ?: GitBundle.message("branch.direction.panel.select.link")
      head.text = headText
      head.toolTipText = headText

      with(changesWarningLabel) {
        if (headRepo != null && headBranch != null && headBranch is GitLocalBranch) {
          val pushTarget = findPushTarget(headRepo.gitRepository, headRepo.gitRemote, headBranch)
          if (pushTarget == null) {
            toolTipText = GitBundle.message("branch.direction.panel.warning.push", headBranch.name, headRepo.gitRemote.name)
            isVisible = true
            return@with
          }
          else {
            val repo = headRepo.gitRepository
            val localHash = repo.branches.getHash(headBranch)
            val remoteHash = repo.branches.getHash(pushTarget.branch)
            if (localHash != remoteHash) {
              toolTipText = GitBundle.message("branch.direction.panel.warning.not.synced", headBranch.name, pushTarget.branch.name)
              isVisible = true
              return@with
            }
          }
        }
        isVisible = false
      }
    }

    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      add(base, CC().minWidth("${UI.scale(30)}"))
      add(JLabel(" ${UIUtil.leftArrow()} ").apply {
        foreground = UIUtil.getInactiveTextColor()
        border = JBUI.Borders.empty(0, 5)
      })
      add(head, CC().minWidth("${UI.scale(30)}"))
      add(changesWarningLabel, CC().gapLeft("${UI.scale(10)}"))
    }
  }

  companion object {
    private fun <RepoMapping : GitRepositoryMappingData> createRemoteBranchModel(repoMapping: RepoMapping,
                                                                                 currentRemoteBranch: GitRemoteBranch?): MutableCollectionComboBoxModel<GitRemoteBranch> {
      val branchModel = MutableCollectionComboBoxModel<GitRemoteBranch>().apply {
        val remote = repoMapping.gitRemote
        val branches = repoMapping.gitRepository.branches.remoteBranches.filter {
          it.remote == remote
        }
        replaceAll(branches.sortedWith(BRANCHES_COMPARATOR))
        selectedItem = currentRemoteBranch.takeIf { it != null && branches.contains(it) }
      }
      return branchModel
    }

    private fun <RepoMapping : GitRepositoryMappingData> chooseBaseBranch(parentComponent: JComponent,
                                                                          currentBranch: GitRemoteBranch?,
                                                                          repoMapping: RepoMapping,
                                                                          applySelection: (GitRemoteBranch?) -> Unit) {
      val branchModel = createRemoteBranchModel(repoMapping, currentBranch)
      val popup = createBranchPopup(branchModel,
                                    GitBundle.message("branch.direction.panel.base.repo.label"),
                                    JBTextField(repoMapping.repositoryPath).apply {
                                      isEnabled = false
                                    }) {
        applySelection(branchModel.selected)
      }
      popup.showUnderneathOf(parentComponent)
    }


    private fun <RepoMapping : GitRepositoryMappingData> chooseHeadRepoAndBranch(parentComponent: JComponent,
                                                                                 currentRepo: RepoMapping?,
                                                                                 currentBranch: GitBranch?,
                                                                                 applySelection: (RepoMapping?, GitBranch?) -> Unit,
                                                                                 items: List<RepoMapping>) {

      val repoModel: CollectionComboBoxModel<RepoMapping> = CollectionComboBoxModel(items, currentRepo)
      val branchModel = MutableCollectionComboBoxModel<GitBranch>()

      repoModel.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent) {}
        override fun intervalRemoved(e: ListDataEvent) {}
        override fun contentsChanged(e: ListDataEvent) {
          if (e.index0 == -1 && e.index1 == -1) updateHeadBranches(branchModel, repoModel.selected)
        }
      })
      updateHeadBranches(branchModel, repoModel.selected)

      if (currentBranch != null && branchModel.items.contains(currentBranch)) branchModel.selectedItem = currentBranch

      val popup = createBranchPopup(branchModel,
                                    GitBundle.message("branch.direction.panel.head.repo.label"),
                                    ComboBox(repoModel).apply {
                                      renderer = SimpleListCellRenderer.create("") { it.repositoryPath }
                                    }) {
        applySelection(repoModel.selected, branchModel.selected)
      }

      popup.showUnderneathOf(parentComponent)
    }

    private fun <RepoMapping : GitRepositoryMappingData> updateHeadBranches(branchModel: MutableCollectionComboBoxModel<GitBranch>,
                                                                            repoMapping: RepoMapping?) {
      val repo = repoMapping?.gitRepository
      if (repo == null) {
        branchModel.replaceAll(emptyList())
        return
      }

      val remote = repoMapping.gitRemote
      val remoteBranches = repo.branches.remoteBranches.filter {
        it.remote == remote
      }

      val branches = repo.branches.localBranches.sortedWith(BRANCHES_COMPARATOR) + remoteBranches.sortedWith(BRANCHES_COMPARATOR)
      branchModel.replaceAll(branches)
      branchModel.selectedItem = repo.currentBranch
    }

    private val BRANCHES_COMPARATOR = Comparator<GitBranch> { b1, b2 -> StringUtil.naturalCompare(b1.name, b2.name) }


    private fun <T : GitBranch> createBranchPopup(branchModel: ComboBoxModel<T>,
                                                  @Nls repoRowMessage: String,
                                                  repoComponent: JComponent,
                                                  onSave: () -> Unit): JBPopup {
      var buttonHandler: ((ActionEvent) -> Unit)? = null

      lateinit var branchComponent: ComboBox<T>
      val panel = panel {
        row(repoRowMessage) {
          cell(repoComponent)
            .horizontalAlign(HorizontalAlign.FILL)
        }
        row(GitBundle.message("branch.direction.panel.branch.label")) {
          branchComponent = comboBox(branchModel, SimpleListCellRenderer.create("", GitBranch::getName))
            .horizontalAlign(HorizontalAlign.FILL)
            .also {
              ComboboxSpeedSearch.installSpeedSearch(it.component, GitBranch::getName)
            }
            .component
        }
        row {
          button(GitBundle.message("branch.direction.panel.save.button")) {
            buttonHandler?.invoke(it)
          }.horizontalAlign(HorizontalAlign.RIGHT)
        }
      }.apply {
        isFocusCycleRoot = true
        border = JBUI.Borders.empty(8, 8, 0, 8)
      }

      return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, repoComponent.takeIf { it.isEnabled } ?: branchComponent)
        .setFocusable(false)
        .createPopup().apply {
          setRequestFocus(true)
        }.also { popup ->
          branchModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent?) {
              invokeLater { popup.pack(true, false) }
            }

            override fun intervalRemoved(e: ListDataEvent?) {
              invokeLater { popup.pack(true, false) }
            }

            override fun contentsChanged(e: ListDataEvent?) {}
          })

          buttonHandler = {
            onSave()
            popup.closeOk(null)
          }
        }
    }
  }
}