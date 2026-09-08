// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.ContentModuleDescriptor
import com.intellij.ide.plugins.DependsSubDescriptor
import com.intellij.ide.plugins.DescriptorExclusionReason
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.ide.plugins.PluginInitializationDiagnosticUtils
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.shortLogDescription
import com.intellij.dev.pluginLoading.PluginLoadingNode.DependencyNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.DescriptorNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.DescriptorReadErrorNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.ExclusionChainNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.GroupNode
import com.intellij.dev.pluginLoading.PluginLoadingStateTreeBuilder.buildPluginLoadingStateTree
import com.intellij.dev.pluginLoading.PluginLoadingStateTreeBuilder.loadingStateNodeId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal class PluginLoadingStateDialog(
  project: Project?,
  private var stateTree: PluginLoadingStateTree,
) : DialogWrapper(project, false, IdeModalityType.MODELESS) {
  private val treeComponent = Tree(DefaultTreeModel(stateTree.root))
  private val details = JBTextArea()
  private val summary = JBLabel()
  private val problemsOnly = JBCheckBox(DevPluginLoadingBundle.message("plugin.loading.state.problems.only"))

  init {
    title = DevPluginLoadingBundle.message("dialog.title.plugin.loading.state")
    setOKButtonText(CommonBundle.getCloseButtonText())
    init()
  }

  override fun getDimensionServiceKey(): String = "internal.plugin.loading.state"

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun createLeftSideActions(): Array<Action> {
    return arrayOf(object : AbstractAction(DevPluginLoadingBundle.message("plugin.loading.state.copy.report")) {
      override fun actionPerformed(e: ActionEvent) {
        CopyPasteManager.copyTextToClipboard(buildReport(stateTree.root))
      }
    })
  }

  override fun createCenterPanel(): JComponent {
    treeComponent.isRootVisible = false
    treeComponent.showsRootHandles = true
    treeComponent.cellRenderer = PluginLoadingStateRenderer()
    // canExpand lets the overlay search walk the whole model, not only the visible rows
    TreeSpeedSearch.installOn(treeComponent, true) { path -> nodeSearchText(path) }
    treeComponent.addTreeSelectionListener { showDetails() }
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean = jumpToRelatedDescriptor()
    }.installOn(treeComponent)

    details.isEditable = false
    details.font = JBUI.Fonts.create(Font.MONOSPACED, JBUI.Fonts.label().size)

    problemsOnly.addActionListener { reload() }

    val header = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyBottom(6)
      add(summary, BorderLayout.WEST)
      add(problemsOnly, BorderLayout.EAST)
    }
    val splitter = JBSplitter(true, 0.7f).apply {
      firstComponent = JBScrollPane(treeComponent)
      secondComponent = JBScrollPane(details)
    }
    val panel = JPanel(BorderLayout()).apply {
      preferredSize = JBUI.size(1000, 700)
      add(header, BorderLayout.NORTH)
      add(splitter, BorderLayout.CENTER)
    }
    updateSummary()
    expandInterestingNodes()
    return panel
  }

  /** The tree holds the focus, so a keystroke starts the speed search at once. */
  override fun getPreferredFocusedComponent(): JComponent = treeComponent

  /** Rebuilds the model from the current plugin set, so a dynamic plugin load is visible. */
  private fun reload() {
    stateTree = buildPluginLoadingStateTree(PluginManagerCore.getPluginSet(), problemsOnly.isSelected)
    treeComponent.model = DefaultTreeModel(stateTree.root)
    details.text = ""
    updateSummary()
    expandInterestingNodes()
  }

  private fun updateSummary() {
    summary.text = DevPluginLoadingBundle.message(
      "plugin.loading.state.summary",
      stateTree.pluginCount, stateTree.loadedPluginCount, stateTree.problemPluginCount, stateTree.descriptorReadErrorCount,
    )
  }

  /**
   * Expands the read error group and every plugin that holds a problem, one level deep.
   * The whole set holds a few thousand nodes, so a full expansion is too slow to read.
   */
  private fun expandInterestingNodes() {
    for (child in stateTree.root.children()) {
      val node = child as DefaultMutableTreeNode
      val userObject = node.userObject
      if (userObject !is GroupNode && !(userObject is DescriptorNode && userObject.state.isProblem)) {
        continue
      }
      treeComponent.expandPath(TreePath(node.path))
      // the path to the root cause is the point of the dialog, so show it without an extra click
      for (grandChild in node.children()) {
        val groupNode = grandChild as DefaultMutableTreeNode
        if ((groupNode.userObject as? GroupNode)?.group == NodeGroup.EXCLUSION_CHAIN) {
          treeComponent.expandPath(TreePath(groupNode.path))
        }
      }
    }
  }

  /** Selects the descriptor a dependency node or an exclusion chain hop points at. */
  private fun jumpToRelatedDescriptor(): Boolean {
    val selected = treeComponent.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return false
    val target = when (val userObject = selected.userObject) {
                   is DependencyNode -> userObject.target
                   is ExclusionChainNode -> userObject.descriptor
                   else -> null
                 } ?: return false
    val targetNode = stateTree.nodeByDescriptor[target] ?: return false
    if (targetNode === selected) {
      return false
    }
    TreeUtil.selectNode(treeComponent, targetNode)
    return true
  }

  private fun showDetails() {
    val selected = treeComponent.lastSelectedPathComponent as? DefaultMutableTreeNode
    details.text = when (val userObject = selected?.userObject) {
      is DescriptorNode -> describeDescriptor(userObject.descriptor, userObject.source)
      is ExclusionChainNode -> describeDescriptor(userObject.descriptor, source = null)
      is DependencyNode -> describeDependency(userObject)
      is DescriptorReadErrorNode -> "${userObject.error.path}\n\n${userObject.error.error.stackTraceToString()}"
      else -> ""
    }
    details.caretPosition = 0
  }

  private fun describeDescriptor(descriptor: IdeaPluginDescriptorImpl, source: PluginsSourceContext?): @NlsSafe String {
    val view = stateTree.view
    return buildString {
      appendLine(descriptor.shortLogDescription)
      appendLine("state: ${view.state(descriptor)}")
      source?.let { appendLine("source: ${sourceName(it)}") }
      when (descriptor) {
        is PluginMainDescriptor -> {
          appendLine("bundled: ${descriptor.isBundled}")
          appendLine("plugin path: ${descriptor.pluginPath}")
        }
        is ContentModuleDescriptor -> {
          appendLine("loading rule: ${descriptor.moduleLoadingRule}")
          appendLine("visibility: ${descriptor.visibility}")
        }
        is DependsSubDescriptor -> appendLine("depends target: ${descriptor.dependsTargetId}")
      }
      appendLine("descriptor path: ${descriptor.descriptorPath}")
      appendLine("package prefix: ${descriptor.packagePrefix ?: "<none>"}")
      appendLine("class loader: ${descriptor.pluginClassLoader ?: "<none>"}")

      val dependencies = view.resolvedDependencies(descriptor)
      if (dependencies.isNotEmpty()) {
        appendLine()
        appendLine("effective dependencies:")
        dependencies.forEach { appendLine("  ${it.shortLogDescription}") }
      }
      val dependents = view.resolvedDependents(descriptor)
      if (dependents.isNotEmpty()) {
        appendLine()
        appendLine("effective dependents:")
        dependents.forEach { appendLine("  ${it.shortLogDescription}") }
      }
    }
  }

  private fun describeDependency(node: DependencyNode): @NlsSafe String = buildString {
    appendLine(dependencyText(node))
    appendLine("declared as: ${node.kind}")
    appendLine("optional: ${node.optional}")
    val target = node.target
    if (target == null) {
      appendLine("no descriptor declares this id")
    }
    else {
      appendLine("resolves to: ${target.shortLogDescription}")
      appendLine("state: ${node.targetState}")
      appendLine()
      appendLine(DevPluginLoadingBundle.message("plugin.loading.state.double.click.hint"))
    }
  }

  private inner class PluginLoadingStateRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      when (val userObject = (value as? DefaultMutableTreeNode)?.userObject) {
        is DescriptorNode -> renderDescriptor(userObject)
        is GroupNode -> append("${groupTitle(userObject.group)} (${userObject.childCount})", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        is DependencyNode -> renderDependency(userObject)
        is ExclusionChainNode -> renderExclusionChainHop(userObject)
        is DescriptorReadErrorNode -> {
          icon = AllIcons.General.Error
          append(userObject.error.path.toString())
        }
      }
      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected)
    }

    private fun renderDescriptor(node: DescriptorNode) {
      icon = stateIcon(node.state)
      val descriptor = node.descriptor
      append(descriptorTitle(descriptor))
      append("  ${descriptor.loadingStateNodeId}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      if (descriptor is PluginMainDescriptor) {
        descriptor.version?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
      }
      if (descriptor is ContentModuleDescriptor && descriptor.moduleLoadingRule != ModuleLoadingRule.OPTIONAL) {
        append("  ${descriptor.moduleLoadingRule.name.lowercase()}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      node.reason?.let {
        append("  ${reasonLabel(it)}", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
      }
    }

    private fun renderDependency(node: DependencyNode) {
      icon = node.targetState?.let { stateIcon(it) } ?: AllIcons.General.Error
      append(dependencyText(node))
      if (node.optional) {
        append("  ${DevPluginLoadingBundle.message("plugin.loading.state.optional")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      if (node.target == null) {
        append("  ${DevPluginLoadingBundle.message("plugin.loading.state.unresolved.id")}", SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
      node.targetReason?.let {
        append("  ${reasonLabel(it)}", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
      }
    }

    private fun renderExclusionChainHop(node: ExclusionChainNode) {
      icon = if (node.isRootCause) AllIcons.General.Error else AllIcons.General.Warning
      append(reasonLabel(node.reason))
      if (node.isRootCause) {
        append("  ${DevPluginLoadingBundle.message("plugin.loading.state.root.cause")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}

/**
 * The reason on one line.
 *
 * A dependency cycle message spans several lines, and a tree label shows only the first one.
 */
private fun reasonLabel(reason: DescriptorExclusionReason): @NlsSafe String =
  PluginInitializationDiagnosticUtils.getLogMessage(reason)
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(separator = " ")

private fun stateIcon(state: DescriptorLoadState): Icon = when (state) {
  DescriptorLoadState.LOADED -> AllIcons.General.InspectionsOK
  DescriptorLoadState.RESOLVED_WITHOUT_CLASS_LOADER -> AllIcons.General.Warning
  DescriptorLoadState.EXCLUDED -> AllIcons.General.Error
  DescriptorLoadState.NOT_A_CANDIDATE -> AllIcons.General.Warning
}

private fun descriptorTitle(descriptor: IdeaPluginDescriptorImpl): @NlsSafe String = when (descriptor) {
  is PluginMainDescriptor -> descriptor.name
  is ContentModuleDescriptor -> descriptor.moduleId.name
  is DependsSubDescriptor -> descriptor.descriptorPath
}

private fun dependencyText(node: DependencyNode): @NlsSafe String = when (node.kind) {
  DependencyKind.MODULE -> "module ${node.id} (${node.namespace})"
  DependencyKind.PLUGIN -> "plugin ${node.id}"
  DependencyKind.DEPENDS -> "<depends> ${node.id}"
}

private fun groupTitle(group: NodeGroup): String = when (group) {
  NodeGroup.CONTENT_MODULES -> DevPluginLoadingBundle.message("plugin.loading.state.group.content.modules")
  NodeGroup.DEPENDS_CONFIGS -> DevPluginLoadingBundle.message("plugin.loading.state.group.depends.configs")
  NodeGroup.DEPENDENCIES -> DevPluginLoadingBundle.message("plugin.loading.state.group.dependencies")
  NodeGroup.EXCLUSION_CHAIN -> DevPluginLoadingBundle.message("plugin.loading.state.group.exclusion.chain")
  NodeGroup.DESCRIPTOR_READ_ERRORS -> DevPluginLoadingBundle.message("plugin.loading.state.group.read.errors")
}

private fun sourceName(source: PluginsSourceContext): @NlsSafe String = when (source) {
  PluginsSourceContext.Product -> "product"
  PluginsSourceContext.Bundled -> "bundled"
  PluginsSourceContext.Custom -> "custom"
  PluginsSourceContext.SystemPropertyProvided -> "system property"
  PluginsSourceContext.ClassPathProvided -> "class path"
}

/**
 * The text the speed search matches. It holds a plugin and a module only.
 *
 * One module id repeats on many dependency rows and on many reason lines, so a match there would bury the module node.
 */
private fun nodeSearchText(path: TreePath): String {
  return when (val userObject = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
    is DescriptorNode -> descriptorSearchText(userObject.descriptor)
    is DescriptorReadErrorNode -> userObject.error.path.toString()
    else -> ""
  }
}

/** The name and the id of a descriptor. It never localizes, because it comes from a descriptor file. */
private fun descriptorSearchText(descriptor: IdeaPluginDescriptorImpl): String = when (descriptor) {
  is PluginMainDescriptor -> "${descriptor.name} ${descriptor.pluginId.idString}"
  is ContentModuleDescriptor -> "${descriptor.moduleId.name} ${descriptor.moduleId.namespace}"
  is DependsSubDescriptor -> descriptor.descriptorPath
}

/** Renders the whole tree as indented text, so the report can go into an issue. */
private fun buildReport(root: DefaultMutableTreeNode): String = buildString {
  fun write(node: DefaultMutableTreeNode, indent: Int) {
    val userObject = node.userObject
    if (userObject != null) {
      repeat(indent) { append("  ") }
      when (userObject) {
        is DescriptorNode -> {
          append("[${userObject.state}] ${userObject.descriptor.shortLogDescription}")
          userObject.reason?.let { append(" -- ${PluginInitializationDiagnosticUtils.getLogMessage(it)}") }
        }
        is GroupNode -> append("${groupTitle(userObject.group)} (${userObject.childCount})")
        is DependencyNode -> {
          append(dependencyText(userObject))
          append(" -> ${userObject.target?.shortLogDescription ?: "unresolved"}")
          userObject.targetState?.let { append(" [$it]") }
        }
        is ExclusionChainNode -> {
          append(PluginInitializationDiagnosticUtils.getLogMessage(userObject.reason))
          if (userObject.isRootCause) append("   <-- root cause")
        }
        is DescriptorReadErrorNode -> append("read error: ${userObject.error.path}")
        else -> append(userObject.toString())
      }
      appendLine()
    }
    for (child in node.children()) {
      write(child as DefaultMutableTreeNode, if (userObject == null) indent else indent + 1)
    }
  }
  write(root, 0)
}
