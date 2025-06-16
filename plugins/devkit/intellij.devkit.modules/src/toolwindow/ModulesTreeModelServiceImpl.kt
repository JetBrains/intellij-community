// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.ide.util.treeView.smartTree.TreeModel
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.File
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Implementation of ModulesTreeModelService that manages the modules tree model.
 */
class ModulesTreeModelServiceImpl(
    private val project: Project,
) : ModulesTreeModelService {
    private val rootNode = DefaultMutableTreeNode("Modules")
    private var treeModel = DefaultTreeModel(rootNode)
    private var allModules: List<JpsModule> = emptyList()


    private var modulePrefix: String = ""

    /**
     * Updates the list of modules in the tree based on the current filter.
     */
    override suspend fun updateModulesList(prefix: String): DefaultTreeModel {
        modulePrefix = prefix
        treeModel = DefaultTreeModel(rootNode)

        withContext(Dispatchers.IO) {
          applyFilter()
        }

        return treeModel
    }

    /**
     * Applies the current filter to show/hide nodes.
     * This method ensures that all necessary elements are always added to the tree,
     * regardless of whether there are filters or not.
     */
    private suspend fun applyFilter() {
        // If this is the first time, load all modules
        if (allModules.isEmpty()) {
            try {
                // Get all modules from the service
                allModules = JpsModulesService.getInstance(project).getAllModules()
            } catch (e: Exception) {
                // If there's an error loading the JPS modules, add an error node
                withContext(Dispatchers.Main) {
                    rootNode.removeAllChildren()
                    val errorNode = DefaultMutableTreeNode("Error loading JPS modules: ${e.message}")
                    rootNode.add(errorNode)
                    treeModel.reload()
                }
                return
            }
        }

        // Get the filtered modules
        val filteredModules = if (modulePrefix.isNotEmpty()) {
            allModules.filter { it.name.startsWith(modulePrefix, ignoreCase = true) }
        } else {
            allModules
        }

        // If we're showing all modules or if the filter has changed significantly, rebuild the tree
        if (modulePrefix.isEmpty() || rootNode.childCount == 0 || 
            (filteredModules.size > rootNode.childCount * 2)) {
            withContext(Dispatchers.Main) {
                // Clear the tree
                rootNode.removeAllChildren()

                // Add all filtered modules to the tree
                for (module in filteredModules) {
                    val moduleNode = createModuleNode(module)
                    rootNode.add(moduleNode)
                }

                // Update the tree
                treeModel.reload()
            }
        } else {
            updateTreeWithFilter(filteredModules)
        }
    }

    /**
     * Removes nodes that don't match the filter.
     */
    private suspend fun removeNodesNotInFilter(filteredModules: List<JpsModule>) {
        withContext(Dispatchers.Main) {
            var i = 0
            while (i < rootNode.childCount) {
                val moduleNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
                val module = moduleNode.userObject as? JpsModule

                if (module != null && !filteredModules.contains(module)) {
                    treeModel.removeNodeFromParent(moduleNode)
                } else {
                    i++
                }
            }
        }
    }

    /**
     * Updates the tree by removing nodes that don't match the filter and adding nodes that match the filter but aren't in the tree.
     */
    private suspend fun updateTreeWithFilter(filteredModules: List<JpsModule>) {
        // Get the current modules in the tree
        val currentModules = getCurrentModulesInTree()

        // Remove nodes that don't match the filter
        removeNodesNotInFilter(filteredModules)

        // Add nodes that match the filter but aren't in the tree
        withContext(Dispatchers.Main) {
            for (module in filteredModules) {
                if (!currentModules.contains(module)) {
                    // Create a new node for this module
                    val moduleNode = createModuleNode(module)
                    rootNode.add(moduleNode)
                    treeModel.nodesWereInserted(rootNode, intArrayOf(rootNode.getIndex(moduleNode)))
                }
            }
        }
    }

    /**
     * Gets the current modules in the tree.
     */
    private fun getCurrentModulesInTree(): Set<JpsModule> {
        val currentModules = mutableSetOf<JpsModule>()
        for (i in 0 until rootNode.childCount) {
            val moduleNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
            val module = moduleNode.userObject as? JpsModule
            if (module != null) {
                currentModules.add(module)
            }
        }
        return currentModules
    }

    /**
     * Creates a module node with all its children.
     */
    private fun createModuleNode(module: JpsModule): DefaultMutableTreeNode {
        val moduleNode = DefaultMutableTreeNode(module)

        // Add source roots section
        addSourceRootsSection(module, moduleNode)

        // Add dependencies section
        addDependenciesSection(module, moduleNode)

        return moduleNode
    }

    /**
     * Adds the source roots section to the module node.
     */
    private fun addSourceRootsSection(module: JpsModule, moduleNode: DefaultMutableTreeNode) {
        val sourceRootsNode = DefaultMutableTreeNode("Source Roots")
        var hasSourceRoots = false

        // Map to store resource root nodes for later use with XML files
        val resourceRootNodes = mutableMapOf<String, DefaultMutableTreeNode>()

        // Add Java source roots
        hasSourceRoots = addJavaSourceRoots(module, sourceRootsNode) || hasSourceRoots

        // Add resource roots and store them for later use
        hasSourceRoots = addResourceRoots(module, sourceRootsNode, resourceRootNodes) || hasSourceRoots

        // Add XML files to their respective resource roots
        addXmlFilesToResourceRoots(module, resourceRootNodes)

        // Add test source roots
        hasSourceRoots = addTestSourceRoots(module, sourceRootsNode) || hasSourceRoots

        // Add test resource roots
        hasSourceRoots = addTestResourceRoots(module, sourceRootsNode) || hasSourceRoots

        // Add the source roots node to the module node if it has any children
        if (hasSourceRoots) {
            moduleNode.add(sourceRootsNode)
        }
    }

    /**
     * Adds Java source roots to the source roots node.
     */
    private fun addJavaSourceRoots(module: JpsModule, sourceRootsNode: DefaultMutableTreeNode): Boolean {
        var hasSourceRoots = false
        for (sourceRoot in module.getSourceRoots(JavaSourceRootType.SOURCE)) {
            val sourceRootNode = DefaultMutableTreeNode(ModulesTreeModelService.SourceRootNode(sourceRoot))
            sourceRootsNode.add(sourceRootNode)
            hasSourceRoots = true
        }
        return hasSourceRoots
    }

    /**
     * Adds resource roots to the source roots node and stores them in the provided map.
     */
    private fun addResourceRoots(
        module: JpsModule,
        sourceRootsNode: DefaultMutableTreeNode,
        resourceRootNodes: MutableMap<String, DefaultMutableTreeNode>
    ): Boolean {
        var hasResourceRoots = false
        for (resourceRoot in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
            val resourceRootNode = DefaultMutableTreeNode(ModulesTreeModelService.ResourceRootNode(resourceRoot))
            sourceRootsNode.add(resourceRootNode)
            hasResourceRoots = true

            // Store the resource root node for later use with XML files
            val rootUrl = resourceRoot.url
            if (rootUrl.startsWith("file://")) {
                resourceRootNodes[rootUrl.substring(7)] = resourceRootNode
            }
        }
        return hasResourceRoots
    }

    /**
     * Adds XML files to their respective resource roots.
     */
    private fun addXmlFilesToResourceRoots(
        module: JpsModule,
        resourceRootNodes: Map<String, DefaultMutableTreeNode>
    ) {
        val xmlParserService = XmlParserService.getInstance(project)
        val xmlFiles = JpsModulesService.getInstance(project).findXmlFilesInModule(module)

        for (xmlFile in xmlFiles) {
            val filePath = xmlFile.file.absolutePath
            // Find the resource root that contains this file
            val resourceRootPath = resourceRootNodes.keys.find { filePath.startsWith(it) }
            if (resourceRootPath != null) {
                val resourceRootNode = resourceRootNodes[resourceRootPath]

                // Parse the XML file and create a wrapper with additional information
                val xmlNodeWithInfo = when (xmlFile) {
                    is JpsModulesService.PluginXmlFile -> {
                        val pluginInfo = xmlParserService.parsePluginXml(xmlFile.file)
                        val xmlNode = DefaultMutableTreeNode(ModulesTreeModelService.PluginXmlFileWithInfo(xmlFile.file, pluginInfo))

                        // Add content modules as child nodes
                        if (pluginInfo != null && pluginInfo.contentModules.isNotEmpty()) {
                            val contentNode = DefaultMutableTreeNode("Content Modules")
                            for (contentModule in pluginInfo.contentModules) {
                                contentNode.add(DefaultMutableTreeNode(ModulesTreeModelService.ContentModuleNode(contentModule, module.name)))
                            }
                            xmlNode.add(contentNode)
                        }

                        // Add dependencies as child nodes
                        if (pluginInfo != null && (pluginInfo.dependencyPlugins.isNotEmpty() || pluginInfo.oldFashionDependencies.isNotEmpty())) {
                            val dependenciesNode = DefaultMutableTreeNode("Dependencies")

                            // Add plugin dependencies
                            for (dependencyPlugin in pluginInfo.dependencyPlugins) {
                                dependenciesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.DependencyPluginNode(dependencyPlugin)))
                            }

                            // Add old-fashion dependencies
                            for (oldFashionDependency in pluginInfo.oldFashionDependencies) {
                                dependenciesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.OldFashionDependencyNode(oldFashionDependency)))
                            }

                            xmlNode.add(dependenciesNode)
                        }

                        // Add module values as child nodes
                        if (pluginInfo != null && pluginInfo.modules.isNotEmpty()) {
                            val modulesNode = DefaultMutableTreeNode("Modules")
                            for (moduleValue in pluginInfo.modules) {
                                modulesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.ModuleValueNode(moduleValue)))
                            }
                            xmlNode.add(modulesNode)
                        }

                        xmlNode
                    }
                    is JpsModulesService.ModuleXmlFile -> {
                        val moduleInfo = xmlParserService.parseModuleXml(xmlFile.file)
                        val xmlNode = DefaultMutableTreeNode(ModulesTreeModelService.ModuleXmlFileWithInfo(xmlFile.file, moduleInfo))

                        // Add content modules as child nodes
                        if (moduleInfo != null && moduleInfo.contentModules.isNotEmpty()) {
                            val contentNode = DefaultMutableTreeNode("Content Modules")
                            for (contentModule in moduleInfo.contentModules) {
                                contentNode.add(DefaultMutableTreeNode(ModulesTreeModelService.ContentModuleNode(contentModule, module.name)))
                            }
                            xmlNode.add(contentNode)
                        }

                        // Add dependencies as child nodes
                        if (moduleInfo != null && (moduleInfo.dependencyPlugins.isNotEmpty() || moduleInfo.oldFashionDependencies.isNotEmpty())) {
                            val dependenciesNode = DefaultMutableTreeNode("Dependencies")

                            // Add plugin dependencies
                            for (dependencyPlugin in moduleInfo.dependencyPlugins) {
                                dependenciesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.DependencyPluginNode(dependencyPlugin)))
                            }

                            // Add old-fashion dependencies
                            for (oldFashionDependency in moduleInfo.oldFashionDependencies) {
                                dependenciesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.OldFashionDependencyNode(oldFashionDependency)))
                            }

                            xmlNode.add(dependenciesNode)
                        }

                        // Add module values as child nodes
                        if (moduleInfo != null && moduleInfo.modules.isNotEmpty()) {
                            val modulesNode = DefaultMutableTreeNode("Modules")
                            for (moduleValue in moduleInfo.modules) {
                                modulesNode.add(DefaultMutableTreeNode(ModulesTreeModelService.ModuleValueNode(moduleValue)))
                            }
                            xmlNode.add(modulesNode)
                        }

                        xmlNode
                    }
                    else -> DefaultMutableTreeNode(xmlFile)
                }

                resourceRootNode?.add(xmlNodeWithInfo)
            }
        }
    }

    /**
     * Adds test source roots to the source roots node.
     */
    private fun addTestSourceRoots(module: JpsModule, sourceRootsNode: DefaultMutableTreeNode): Boolean {
        var hasTestRoots = false
        for (testRoot in module.getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
            val testRootNode = DefaultMutableTreeNode(ModulesTreeModelService.TestRootNode(testRoot))
            sourceRootsNode.add(testRootNode)
            hasTestRoots = true
        }
        return hasTestRoots
    }

    /**
     * Adds test resource roots to the source roots node.
     */
    private fun addTestResourceRoots(module: JpsModule, sourceRootsNode: DefaultMutableTreeNode): Boolean {
        var hasTestResourceRoots = false
        for (testResourceRoot in module.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)) {
            val testResourceRootNode = DefaultMutableTreeNode(ModulesTreeModelService.TestResourceRootNode(testResourceRoot))
            sourceRootsNode.add(testResourceRootNode)
            hasTestResourceRoots = true
        }
        return hasTestResourceRoots
    }

    /**
     * Adds the dependencies section to the module node.
     */
    private fun addDependenciesSection(module: JpsModule, moduleNode: DefaultMutableTreeNode) {
        val dependenciesNode = DefaultMutableTreeNode("Dependencies")
        var hasDependencies = false

        // Add module dependencies
        for (dependency in module.dependenciesList.dependencies) {
            when (dependency) {
                is JpsModuleDependency -> {
                    val dependencyNode = DefaultMutableTreeNode(ModulesTreeModelService.ModuleDependencyNode(dependency))
                    dependenciesNode.add(dependencyNode)
                    hasDependencies = true
                }
                is JpsLibraryDependency -> {
                    val dependencyNode = DefaultMutableTreeNode(ModulesTreeModelService.LibraryDependencyNode(dependency))
                    dependenciesNode.add(dependencyNode)
                    hasDependencies = true
                }
            }
        }

        // Add the dependencies node to the module node if it has any children
        if (hasDependencies) {
            moduleNode.add(dependenciesNode)
        }
    }
}
