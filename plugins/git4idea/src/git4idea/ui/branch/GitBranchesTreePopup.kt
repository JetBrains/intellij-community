// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClientProperty
import com.intellij.ui.popup.NextStepHandler
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.Invoker
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class GitBranchesTreePopup(project: Project, step: GitBranchesTreePopupStep)
  : WizardPopup(project, null, step),
    TreePopup, NextStepHandler {

  private lateinit var tree: JTree

  private var showingChildPath: TreePath? = null
  private var pendingChildPath: TreePath? = null

  private val treeStep: GitBranchesTreePopupStep
    get() = step as GitBranchesTreePopupStep

  init {
    setMinimumSize(JBDimension(200, 200))
    dimensionServiceKey = GitBranchPopup.DIMENSION_SERVICE_KEY
    setSpeedSearchAlwaysShown()
  }

  override fun createContent(): JComponent {
    val treeDisposable = Disposer.newDisposable().also {
      Disposer.register(this, it)
    }

    val treeModel = StructureTreeModel(treeStep.structure,
                                       null,
                                       Invoker.forEventDispatchThread(treeDisposable),
                                       treeDisposable)
    tree = Tree(treeModel).also {
      configureTreePresentation(it)
      overrideTreeActions(it)
      addTreeMouseControlsListeners(it)
    }
    return tree
  }

  private fun configureTreePresentation(tree: JTree) = with(tree) {
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { JBUI.CurrentTheme.Tree.background(true, true) })
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_FOREGROUND, Supplier { JBUI.CurrentTheme.Tree.foreground(true, true) })

    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    isRootVisible = false
    showsRootHandles = true

    cellRenderer = Renderer()

    accessibleContext.accessibleName = GitBundle.message("git.branches.popup.tree.accessible.name")

    ClientProperty.put(this, DefaultTreeUI.LARGE_MODEL_ALLOWED, true)
    rowHeight = JBUIScale.scale(20)
    isLargeModel = true
    expandsSelectedPaths = true
  }

  private fun overrideTreeActions(tree: JTree) = with(tree) {
    val oldToggleAction = actionMap["toggle"]
    actionMap.put("toggle", object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        val path = tree.selectionPath
        if (path != null && tree.model.getChildCount(path.lastPathComponent) == 0) {
          handleSelect(true, null)
          return
        }
        oldToggleAction.actionPerformed(e)
      }
    })

    val oldExpandAction = actionMap["selectChild"]
    actionMap.put("selectChild", object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        val path = tree.selectionPath
        if (path != null && tree.model.getChildCount(path.lastPathComponent) == 0) {
          handleSelect(false, null)
          return
        }
        oldExpandAction.actionPerformed(e)
      }
    })
  }

  private fun addTreeMouseControlsListeners(tree: JTree) = with(tree) {
    addMouseMotionListener(SelectionMouseMotionListener())
    addMouseListener(SelectOnClickListener())
  }

  override fun getActionMap(): ActionMap = tree.actionMap

  override fun getInputMap(): InputMap = tree.inputMap

  override fun afterShow() {
    TreeUtil.expand(tree, TreeVisitor { path ->
      val userObject = TreeUtil.getUserObject(path.lastPathComponent)

      //TODO: move to tree step, pre-build the path
      if (userObject is AbstractTreeNode<*> && userObject.value is GitBranch && (userObject.value as GitBranch).name == "master") {
        return@TreeVisitor TreeVisitor.Action.INTERRUPT
      }
      TreeVisitor.Action.CONTINUE
    }, Consumer {
      tree.selectionPath = it
    })
  }

  override fun isResizable(): Boolean = true

  private inner class SelectionMouseMotionListener : MouseMotionAdapter() {
    private var lastMouseLocation: Point? = null

    /**
     * this method should be changed only in par with
     * [com.intellij.ui.popup.list.ListPopupImpl.MyMouseMotionListener.isMouseMoved]
     */
    private fun isMouseMoved(location: Point): Boolean {
      if (lastMouseLocation == null) {
        lastMouseLocation = location
        return false
      }
      val prev = lastMouseLocation
      lastMouseLocation = location
      return prev != location
    }

    override fun mouseMoved(e: MouseEvent) {
      if (!isMouseMoved(e.locationOnScreen)) return
      val path = getPath(e)
      if (path != null) {
        tree.selectionPath = path
        notifyParentOnChildSelection()
        if (treeStep.isSelectable(TreeUtil.getUserObject(path.lastPathComponent))) {
          tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          if (pendingChildPath == null || pendingChildPath != path) {
            pendingChildPath = path
            restartTimer()
          }
          return
        }
      }
      tree.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    }
  }

  private inner class SelectOnClickListener : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      if (e.button != MouseEvent.BUTTON1) return
      val path = getPath(e) ?: return
      val selected = path.lastPathComponent
      if (treeStep.isSelectable(TreeUtil.getUserObject(selected))) {
        handleSelect(true, e)
      }
    }
  }

  private fun getPath(e: MouseEvent): TreePath? = tree.getClosestPathForLocation(e.point.x, e.point.y)

  private fun handleSelect(handleFinalChoices: Boolean, e: MouseEvent?) {
    val selectionPath = tree.selectionPath
    val pathIsAlreadySelected = showingChildPath != null && showingChildPath == selectionPath
    if (pathIsAlreadySelected) return
    pendingChildPath = null
    val selected = tree.lastSelectedPathComponent
    if (selected != null) {
      val userObject = TreeUtil.getUserObject(selected)
      if (treeStep.isSelectable(userObject)) {
        disposeChildren()

        val hasNextStep = myStep.hasSubstep(userObject)
        if (!hasNextStep && !handleFinalChoices) {
          showingChildPath = null
          return
        }

        val queriedStep = PopupImplUtil.prohibitFocusEventsInHandleSelect().use {
          myStep.onChosen(userObject, handleFinalChoices)
        }

        if (queriedStep == PopupStep.FINAL_CHOICE || !hasNextStep) {
          setFinalRunnable(myStep.finalRunnable)
          setOk(true)
          disposeAllParents(e)
        }
        else {
          showingChildPath = selectionPath
          handleNextStep(queriedStep, selectionPath as Any)
          showingChildPath = null
        }
      }
    }
  }

  override fun handleNextStep(nextStep: PopupStep<*>?, parentValue: Any) {
    val pathBounds = tree.getPathBounds(tree.selectionPath)!!
    val point = Point(pathBounds.x, pathBounds.y)
    SwingUtilities.convertPointToScreen(point, tree)

    myChild = createPopup(this, nextStep, parentValue)
    myChild.show(content, content.locationOnScreen.x + content.width - STEP_X_PADDING, point.y, true)
  }

  override fun onSpeedSearchPatternChanged() {}

  override fun getPreferredFocusableComponent(): JComponent = tree

  override fun onChildSelectedFor(value: Any) {
    val path = value as TreePath
    if (tree.selectionPath != path) {
      tree.selectionPath = path
    }
  }

  private inner class Renderer : TreeCellRenderer {
    private val nodeRenderer = NodeRenderer()
    private val arrowLabel = JLabel()
    private val panel = JBUI.Panels.simplePanel(nodeRenderer)
      .addToRight(arrowLabel)
      .withBorder(JBUI.Borders.emptyRight(2))
      .andTransparent()

    override fun getTreeCellRendererComponent(tree: JTree?,
                                              value: Any?,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      nodeRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, true)

      val userObject = TreeUtil.getUserObject(value)
      arrowLabel.apply {
        isVisible = step.hasSubstep(userObject)
        icon = if (selected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
      }
      return panel
    }
  }

  companion object {

    @JvmStatic
    fun show(project: Project, repository: GitRepository) {
      GitBranchesTreePopup(project, GitBranchesTreePopupStep(project, repository)).showCenteredInCurrentWindow(project)
    }
  }
}