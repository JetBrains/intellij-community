// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import org.jetbrains.jps.model.module.JpsModule
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Panel that displays a list of all JPS modules in the project.
 */
class ModulesPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val tree: Tree
    private var modulePrefix: String = ""
    private var filterField: JBTextField? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null
    private val modulesTreeModel = ModulesTreeModel(project)
    private lateinit var treeNavigator: ModulesTreeNavigator

    /**
     * Resets the filter to empty string
     */
    private suspend fun resetFilter() {
        filterField?.text = ""
        modulePrefix = ""
        updateModulesList()
    }

    init {
        // Create the tree with modules
        tree = Tree()
        tree.isRootVisible = false
        tree.showsRootHandles = true

        // Set custom cell renderer
        tree.cellRenderer = ModuleTreeCellRenderer()


        // Add speed search functionality
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree)

        // Add popup menu
        val actionManager = ActionManager.getInstance()
        val popupActionGroup = actionManager.getAction("ModulesTreePopupMenu") as? ActionGroup
        if (popupActionGroup != null) {
            PopupHandler.installPopupMenu(tree, popupActionGroup, ActionPlaces.POPUP)
        }

        // Initialize the tree navigator
        treeNavigator = ModulesTreeNavigator(project, tree) { desiredPrefix ->
          if (!desiredPrefix.startsWith(modulePrefix)) {
            resetFilter()
          }
        }

        // Register F4 key shortcut for navigation
        treeNavigator.registerF4KeyShortcut(coroutineScope)

        // Create toolbar with collapse all button and filter
        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val collapseAllButton = createCollapseAllButton()
        toolbarPanel.add(collapseAllButton)

        // Add filter components
        val filterLabel = JBLabel("Filter by prefix: ")
        toolbarPanel.add(filterLabel)

        val newFilterField = JBTextField(15)
        this.filterField = newFilterField
        newFilterField.toolTipText = "Enter module prefix to filter"
        newFilterField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                scheduleFilterUpdate()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                scheduleFilterUpdate()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                scheduleFilterUpdate()
            }

            private fun scheduleFilterUpdate() {
                modulePrefix = newFilterField.text.trim()

                // Cancel previous update job if it's still running
                updateJob?.cancel()

                // Schedule a new update job with a small delay to avoid UI lag during typing
                updateJob = coroutineScope.launch {
                    delay(300) // 300ms debounce delay
                    withContext(Dispatchers.Main) {
                        updateModulesList()
                    }
                }
            }
        })
        toolbarPanel.add(newFilterField)

        // Add clear button
        val clearButton = JBLabel(AllIcons.Actions.Cancel)
        clearButton.toolTipText = "Clear filter"
        clearButton.border = JBUI.Borders.empty(2)
        clearButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
              coroutineScope.launch {
                resetFilter()
              }
            }
        })
        toolbarPanel.add(clearButton)

        add(toolbarPanel, BorderLayout.NORTH)

        // Populate the tree with modules
        coroutineScope.launch {
          updateModulesList()
        }

        // Add the tree to a scroll pane
        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createCollapseAllButton(): JBLabel {
        val collapseAllButton = JBLabel(AllIcons.Actions.Collapseall)
        collapseAllButton.toolTipText = "Collapse All"
        collapseAllButton.border = JBUI.Borders.empty(2)
        collapseAllButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                TreeUtil.collapseAll(tree, -1)
            }
        })
        return collapseAllButton
    }

    /**
     * Updates the list of modules in the tree based on the current filter.
     */
    private suspend fun updateModulesList() {
        // Save the expansion state before updating by collecting user objects instead of paths
        val expandedPathNames = withContext(Dispatchers.Main) {
           TreeUtil.collectExpandedPaths(tree).map { it.path.map { it.toString() } }
        }

        // Update the tree model with the current filter
        val treeModel = modulesTreeModel.updateModulesList(modulePrefix)

        // Restore the expansion state on the main thread
        withContext(Dispatchers.Main) {
          val rootNode = treeModel.root as DefaultMutableTreeNode

          // Find matching paths in new tree and collect them
          val newPaths = expandedPathNames.mapNotNull { pathNames ->
            var currentNode = rootNode
            var path: Array<Any> = arrayOf(currentNode)

            // Try to match each path component
            for (name in pathNames.drop(1)) { // Skip root node
              val found = currentNode.children().asSequence().map { it as DefaultMutableTreeNode }
                .find { it.userObject.toString() == name }

              if (found != null) {
                currentNode = found
                path += found
              }
              else {
                return@mapNotNull null // Path component not found
              }
            }
            javax.swing.tree.TreePath(path)
          }

          // Restore expansion state with the new paths
          tree.model = treeModel
          TreeUtil.restoreExpandedPaths(tree, newPaths)

        }
    }

  /**
     * Custom cell renderer for the module tree.
     */
    private class ModuleTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            if (value !is DefaultMutableTreeNode) return
            val userObject = value.userObject

            when (userObject) {
                is JpsModule -> {
                    // Module node
                    icon = AllIcons.Nodes.Module
                    append(userObject.name)
                }
                is String -> {
                    // Section node (e.g., "Source Roots", "Dependencies")
                    append(userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is ModulesTreeModelService.SourceRootNode -> {
                    // Source root node
                    icon = AllIcons.Modules.SourceRoot
                    val url = userObject.sourceRoot.url
                    val path = url.removePrefix("file://")
                    append(path.substringAfterLast('/'))
                    append(" (${path.substringBeforeLast('/')})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.ResourceRootNode -> {
                    // Resource root node
                    icon = AllIcons.Modules.ResourcesRoot
                    val url = userObject.sourceRoot.url
                    val path = url.removePrefix("file://")
                    append(path.substringAfterLast('/'))
                    append(" (${path.substringBeforeLast('/')})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.TestRootNode -> {
                    // Test root node
                    icon = AllIcons.Nodes.TestSourceFolder
                    val url = userObject.sourceRoot.url
                    val path = url.removePrefix("file://")
                    append(path.substringAfterLast('/'))
                    append(" (${path.substringBeforeLast('/')})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.TestResourceRootNode -> {
                    // Test resource root node
                    icon = AllIcons.Modules.TestResourcesRoot
                    val url = userObject.sourceRoot.url
                    val path = url.removePrefix("file://")
                    append(path.substringAfterLast('/'))
                    append(" (${path.substringBeforeLast('/')})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.ModuleDependencyNode -> {
                    // Module dependency node
                    icon = AllIcons.Nodes.Module
                    val moduleName = userObject.dependency.module?.name ?: "Unknown"
                    append(moduleName)
                    append(" (module)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.LibraryDependencyNode -> {
                    // Library dependency node
                    icon = AllIcons.Nodes.PpLib
                    val libraryName = userObject.dependency.library?.name ?: "Unknown"
                    append(libraryName)
                    append(" (library)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.PluginXmlFileWithInfo -> {
                    // Plugin XML file node
                    icon = AllIcons.FileTypes.Xml
                    append(userObject.file.name)
                    if (userObject.pluginInfo != null) {
                        val id = userObject.pluginInfo.id
                        if (id != null) {
                            append(" (ID: $id)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                    }
                }
                is ModulesTreeModelService.ModuleXmlFileWithInfo -> {
                    // Module XML file node
                    icon = AllIcons.FileTypes.Xml
                    append(userObject.file.name)
                    if (userObject.moduleInfo != null) {
                        val id = userObject.moduleInfo.id
                        if (id != null) {
                            append(" (ID: $id)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                    }
                }
                is JpsModulesService.PluginXmlFile -> {
                    // Plugin XML file node (without info)
                    icon = AllIcons.FileTypes.Xml
                    append(userObject.file.name)
                }
                is JpsModulesService.ModuleXmlFile -> {
                    // Module XML file node (without info)
                    icon = AllIcons.FileTypes.Xml
                    append(userObject.file.name)
                }
                is ModulesTreeModelService.ContentModuleNode -> {
                    // Content module node
                    icon = AllIcons.Nodes.Module
                    append(userObject.contentModule.name)
                    val pluginInfo = if (userObject.pluginModuleName != null) {
                        " (${userObject.contentModule.loading}, plugin: ${userObject.pluginModuleName})"
                    } else {
                        " (${userObject.contentModule.loading})"
                    }
                    append(pluginInfo, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ModulesTreeModelService.DependencyPluginNode -> {
                    // Dependency plugin node
                    icon = AllIcons.Nodes.Plugin
                    append(userObject.dependencyPlugin.id)
                }
                is ModulesTreeModelService.OldFashionDependencyNode -> {
                    // Old fashion dependency node
                    icon = AllIcons.Nodes.Plugin
                    append(userObject.oldFashionDependency.id)
                    if (userObject.oldFashionDependency.optional) {
                        append(" (optional)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }
                is ModulesTreeModelService.ModuleValueNode -> {
                    // Module value node
                    icon = AllIcons.Nodes.Module
                    append(userObject.moduleValue.value)
                }
                else -> {
                    // Unknown node type
                    append(userObject.toString())
                }
            }
        }
    }
}
