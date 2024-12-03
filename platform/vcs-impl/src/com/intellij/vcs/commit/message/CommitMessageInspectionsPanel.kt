// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
import com.intellij.ide.util.treeView.TreeState
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel
import com.intellij.profile.codeInspection.ui.ToolDescriptors
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeRenderer
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable.InspectionsConfigTreeTableSettings
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

@ApiStatus.Internal
class CommitMessageInspectionsPanel(private val myProject: Project) : Disposable {
  private val myInitialToolDescriptors: MutableList<ToolDescriptors> = ArrayList<ToolDescriptors>()
  private val myToolDetails: MutableMap<HighlightDisplayKey, CommitMessageInspectionDetails> = HashMap<HighlightDisplayKey, CommitMessageInspectionDetails>()
  private val myRoot: InspectionConfigTreeNode = InspectionConfigTreeNode.Group("")
  private var myModifiableModel: InspectionProfileModifiableModel
  private val myInspectionsTable: InspectionsConfigTreeTable

  private lateinit var myDetailsPanel: Placeholder
  val component: JComponent

  init {
    myModifiableModel = createProfileModel()
    myInspectionsTable = createInspectionsTable()
    val detailsPanel = panel { row { myDetailsPanel = placeholder() } }

    val splitter = JBSplitter("CommitMessageInspectionsPanelSplitter", 0.5f)
    splitter.setShowDividerIcon(false)
    splitter.setHonorComponentsMinimumSize(false)
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myInspectionsTable, false))
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(detailsPanel, true))

    component = object : BorderLayoutPanel() {
      override fun getPreferredSize(): Dimension? {
        val size = super.getPreferredSize()
        size.height = JBUI.scale(120)
        return size
      }
    }.also {
      it.addToCenter(splitter)
    }
  }

  private fun createProfileModel(): InspectionProfileModifiableModel {
    val profile = CommitMessageInspectionProfile.getInstance(myProject)
    return InspectionProfileModifiableModel(profile)
  }

  private fun initToolDescriptors() {
    myInitialToolDescriptors.clear()
    for (state in myModifiableModel.getDefaultStates(myProject)) {
      myInitialToolDescriptors.add(ToolDescriptors.fromScopeToolState(state, myModifiableModel, myProject))
    }
  }

  private fun buildInspectionsModel() {
    myRoot.removeAllChildren()
    for (toolDescriptors in myInitialToolDescriptors) {
      myRoot.add(MyInspectionTreeNode(toolDescriptors))
    }
    TreeUtil.sortRecursively(myRoot, InspectionsConfigTreeComparator.INSTANCE)
  }

  private fun createInspectionsTable(): InspectionsConfigTreeTable {
    val table = InspectionsConfigTreeTable.create(MyInspectionsTableSettings(myRoot, myProject), this)
    table.setRootVisible(false)
    table.setTreeCellRenderer(MyInspectionsTreeRenderer())
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.tree.getSelectionModel().selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    table.tree.addTreeSelectionListener(TreeSelectionListener { updateDetailsPanel() })
    return table
  }

  private fun updateDetailsPanel() {
    val node = getSelectedNode()
    val currentDetails = if (node != null) myToolDetails.computeIfAbsent(node.key) { createDetails(node) } else null

    myDetailsPanel.component = currentDetails?.component
    currentDetails?.update()
  }

  private fun createDetails(node: MyInspectionTreeNode): CommitMessageInspectionDetails {
    val details = CommitMessageInspectionDetails(myProject, myModifiableModel, node.defaultDescriptor)
    details.addListener(object : CommitMessageInspectionDetails.ChangeListener {
      override fun onSeverityChanged(severity: HighlightSeverity) {
        myInspectionsTable.updateUI()
      }
    })
    return details
  }

  private fun getSelectedNode(): MyInspectionTreeNode? {
    val selectedPath = myInspectionsTable.tree.selectionPath ?: return null
    return selectedPath.lastPathComponent as MyInspectionTreeNode
  }

  override fun dispose() {
    clearToolDetails()
  }

  fun reset() {
    clearToolDetails()

    myModifiableModel.getAllTools().forEach { it.resetConfigPanel() }
    myModifiableModel = createProfileModel()
    initToolDescriptors()

    val inspectionsTree = myInspectionsTable.tree
    val treeState = TreeState.createOn(inspectionsTree, myRoot)
    buildInspectionsModel()
    (inspectionsTree.model as DefaultTreeModel).reload()
    treeState.applyTo(inspectionsTree, myRoot)
    TreeUtil.ensureSelection(inspectionsTree)
  }

  private fun clearToolDetails() {
    for (details in myToolDetails.values) {
      Disposer.dispose(details)
    }
    myToolDetails.clear()
    myDetailsPanel.component = null
  }

  fun isModified(): Boolean {
    return myInitialToolDescriptors.any { SingleInspectionProfilePanel.areToolDescriptorsChanged(myProject, myModifiableModel, it) } ||
           myToolDetails.values.any { details -> details.component.isModified() }
  }

  @Throws(ConfigurationException::class)
  fun apply() {
    for (details in myToolDetails.values) {
      details.component.apply()
    }
    myModifiableModel.commit()
    myProject.getMessageBus().syncPublisher(CommitMessageInspectionProfile.TOPIC).profileChanged()
    reset()
  }

  private inner class MyInspectionsTableSettings(root: InspectionConfigTreeNode, project: Project)
    : InspectionsConfigTreeTableSettings(root, project) {

    protected override fun getInspectionProfile(): InspectionProfileImpl {
      return myModifiableModel
    }

    protected override fun onChanged(node: InspectionConfigTreeNode) {
    }

    override fun updateRightPanel() {
      updateDetailsPanel()
    }
  }

  private class MyInspectionTreeNode(descriptors: ToolDescriptors)
    : InspectionConfigTreeNode.Tool(Supplier { descriptors }) {

    override fun calculateIsProperSettings(): Boolean = false
  }

  private class MyInspectionsTreeRenderer : InspectionsConfigTreeRenderer() {
    override fun getFilter(): String? = null
  }
}
