// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionPlaces.VCS_LOG_BRANCHES_PLACE
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.ui.ProgressStripe
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.BranchesDashboardActions.DeleteBranchAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowBranchDiffAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ShowMyBranchesAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.ToggleFavoriteAction
import git4idea.ui.branch.dashboard.BranchesDashboardActions.UpdateSelectedBranchAction
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import javax.swing.*

@ApiStatus.Internal
object BranchesDashboardTreeComponent {
  fun create(
    parentDisposable: Disposable,
    project: Project,
    model: BranchesDashboardTreeModel,
    selectionHandler: BranchesDashboardTreeSelectionHandler,
    searchLook: SearchLook = SearchLook.Standalone(),
  ): JComponent {
    val tree = BranchesTreeComponent(project).apply {
      accessibleContext.accessibleName = message("git.log.branches.tree.accessible.name")
    }

    val filteringTree = FilteringBranchesTree(project, model, tree, place = VCS_LOG_BRANCHES_PLACE, disposable = parentDisposable).apply {
      installPasteAction()
    }

    val branchesSearchField = filteringTree.installSearchField().apply {
      accessibleContext.accessibleName = message("git.log.branches.search.field.accessible.name")
      // fixme: this needs to be dynamic
      accessibleContext.accessibleDescription = message("git.log.branches.search.field.accessible.description",
                                                        KeymapUtil.getFirstKeyboardShortcutText("Vcs.Log.FocusTextFilter"))
    }

    val searchComponent = when (searchLook) {
      is SearchLook.Inline -> {
        with(branchesSearchField) {
          textEditor.border = JBUI.Borders.emptyLeft(5)
          apply(UIUtil::setNotOpaqueRecursively)
        }.let {
          Wrapper(it).apply {
            background = UIUtil.getListBackground()
            border = createBorder(SideBorder.BOTTOM)
            setVerticalSizeReferent(searchLook.heightReferent)
          }
        }
      }
      is SearchLook.Standalone -> {
        JPanel(null).apply {
          name = "Size limit wrapper"
          isOpaque = false
          layout = if (searchLook.preferredHeight != null) {
            SizeRestrictedSingleComponentLayout().apply {
              prefSize = DimensionRestrictions.ScalingConstant(height = searchLook.preferredHeight)
              minSize = prefSize
            }
          }
          else {
            BorderLayout()
          }
          border = JBUI.Borders.empty(0, 10)
          add(branchesSearchField)
        }
      }
    }

    val progressStripe = wrapWithProgress(tree, model, parentDisposable)

    val uiController = BranchesDashboardTreeController(project, selectionHandler, model, tree)

    return simplePanel()
      .addToTop(searchComponent)
      .addToCenter(progressStripe)
      .apply {
        isFocusTraversalPolicyProvider = true
        focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
          override fun getDefaultComponent(aContainer: Container): Component? = tree
        }
      }
      .let {
        val treeExpander = DefaultTreeExpander(tree)
        UiDataProvider.wrapComponent(it) { sink ->
          uiController.uiDataSnapshot(sink)
          sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
        }
      }
      .also {
        val actionManager = ActionManager.getInstance()
        actionManager.getAction(IdeActions.ACTION_EXPAND_ALL).registerCustomShortcutSet(it, parentDisposable)
        actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL).registerCustomShortcutSet(it, parentDisposable)
        ShowBranchDiffAction().registerCustomShortcutSet(it, null)
        DeleteBranchAction().registerCustomShortcutSet(it, null)
        createFocusFilterFieldAction(branchesSearchField, tree)
          .registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_FIND), it)
      }
  }

  private fun wrapWithProgress(
    tree: BranchesTreeComponent,
    model: BranchesDashboardTreeModel,
    disposable: Disposable,
  ): ProgressStripe {
    val progressStripe = ProgressStripe(ScrollPaneFactory.createScrollPane(tree, true), disposable)
    fun updateLoadingState() {
      if (model.isLoading) {
        progressStripe.startLoading()
        tree.emptyText.text = message("action.Git.Loading.Branches.progress")
      }
      else {
        progressStripe.stopLoading()
        tree.emptyText.text = StatusText.getDefaultEmptyText()
      }
    }
    model.addListener(object : BranchesTreeModel.Listener {
      override fun onLoadingStateChange() {
        updateLoadingState()
      }
    })
    updateLoadingState()
    return progressStripe
  }

  fun createActionGroup(): DefaultActionGroup {
    val actionManager = ActionManager.getInstance()

    val diffAction = ShowBranchDiffAction()
    val deleteAction = DeleteBranchAction()
    val toggleFavoriteAction = ToggleFavoriteAction()
    val fetchAction = actionManager.getAction("Git.Fetch")
    val showMyBranchesAction = ShowMyBranchesAction()
    val newBranchAction = actionManager.getAction("Git.New.Branch.In.Log")
    val updateSelectedAction = UpdateSelectedBranchAction()
    val expandAllAction = actionManager.getAction(IdeActions.ACTION_EXPAND_ALL)
    val collapseAllAction = actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL)
    val settings = actionManager.getAction("Git.Log.Branches.Settings")

    val group = DefaultActionGroup()
    group.add(newBranchAction)
    group.add(updateSelectedAction)
    group.add(deleteAction)
    group.add(diffAction)
    group.add(showMyBranchesAction)
    group.add(fetchAction)
    group.add(toggleFavoriteAction)
    group.add(actionManager.getAction("Git.Log.Branches.Navigate.Log.To.Selected.Branch"))
    group.add(Separator())
    group.add(settings)
    group.add(actionManager.getAction("Git.Log.Branches.Grouping.Settings"))
    group.add(expandAllAction)
    group.add(collapseAllAction)
    return group
  }

  private fun createFocusFilterFieldAction(searchField: Component, tree: JTree): AnAction =
    DumbAwareAction.create { e ->
      val project = e.getData(CommonDataKeys.PROJECT) ?: return@create
      val focusManager = IdeFocusManager.getInstance(project)
      if (focusManager.getFocusedDescendantFor(tree) != null) {
        focusManager.requestFocus(searchField, true)
      }
      else {
        focusManager.requestFocus(tree, true)
      }
    }

  private fun FilteringBranchesTree.installPasteAction() {
    component.actionMap.put(TransferHandler.getPasteAction().getValue(Action.NAME), object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        val speedSearch = searchModel.speedSearch as? SpeedSearch ?: return
        val pasteContent =
          CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
            // the same filtering logic as in javax.swing.text.PlainDocument.insertString (e.g. DnD to search field)
            ?.let { StringUtil.convertLineSeparators(it, " ") }
        speedSearch.type(pasteContent)
        speedSearch.update()
      }
    })
  }

  sealed interface SearchLook {
    data class Inline(val heightReferent: JComponent? = null) : SearchLook
    data class Standalone(val preferredHeight: Int? = null) : SearchLook
  }
}

@ApiStatus.Internal
interface BranchesDashboardTreeSelectionHandler {
  @get:RequiresEdt
  @set:RequiresEdt
  var selectionAction: SelectionAction?

  @RequiresEdt
  fun filterBy(branches: List<String>, repositories: Set<GitRepository> = emptySet())

  @RequiresEdt
  fun navigateTo(navigatable: VcsLogNavigatable, focus: Boolean)

  /**
   * Mode of handling simple selection without an explicit action
   */
  enum class SelectionAction {
    FILTER,
    NAVIGATE
  }
}