// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Handles navigation in the modules tree.
 */
class ModulesTreeNavigator(
    private val project: Project,
    private val tree: JTree,
    private val resetFilterIfNeeded: suspend (desiredPrefix: String) -> Unit
) {
    /**
     * Navigates to the selected node in the tree.
     * Returns true if navigation was successful, false otherwise.
     */
    suspend fun navigateToSelectedNode(): Boolean {
        val path = tree.selectionPath ?: return false
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
        return navigateToNode(node)
    }

  /**
     * Navigates to the specified node.
     * Returns true if navigation was successful, false otherwise.
     */
    suspend fun navigateToNode(node: DefaultMutableTreeNode): Boolean {
        val userObject = node.userObject

        return when (userObject) {
            is ModulesTreeModelService.SourceRootNode, is ModulesTreeModelService.ResourceRootNode, 
            is ModulesTreeModelService.TestRootNode, is ModulesTreeModelService.TestResourceRootNode -> {
                navigateToSourceRoot(userObject)
            }
            is ModulesTreeModelService.ModuleDependencyNode -> {
                navigateToModuleDependency(userObject)
            }
            is ModulesTreeModelService.LibraryDependencyNode -> {
                // Navigate to the library in the project view if possible
                // For now, we just return true to indicate the event was handled
                true
            }
            is ModulesTreeModelService.PluginXmlFileWithInfo, is ModulesTreeModelService.ModuleXmlFileWithInfo, 
            is JpsModulesService.PluginXmlFile, is JpsModulesService.ModuleXmlFile -> {
                navigateToXmlFile(userObject)
            }
            is ModulesTreeModelService.ContentModuleNode -> {
                navigateToContentModule(userObject)
            }
            is ModulesTreeModelService.DependencyPluginNode -> {
                navigateToDependencyPlugin(userObject)
            }
            is ModulesTreeModelService.OldFashionDependencyNode -> {
                navigateToOldFashionDependency(userObject)
            }
            is ModulesTreeModelService.ModuleValueNode -> {
                // Navigate to the module value if possible
                // For now, we just return true to indicate the event was handled
                true
            }
            else -> false
        }
    }

    private suspend fun navigateToSourceRoot(userObject: Any): Boolean {
        // Get the source root URL
        val sourceRoot = when (userObject) {
            is ModulesTreeModelService.SourceRootNode -> userObject.sourceRoot
            is ModulesTreeModelService.ResourceRootNode -> userObject.sourceRoot
            is ModulesTreeModelService.TestRootNode -> userObject.sourceRoot
            is ModulesTreeModelService.TestResourceRootNode -> userObject.sourceRoot
            else -> null
        }

        // Convert URL to file path and navigate to it in project view
        if (sourceRoot != null) {
            val url = sourceRoot.url
            // URL format is typically "file://path/to/directory"
            val filePath = url.removePrefix("file://")
            val virtualFile = getVirtualFile(filePath)
            if (virtualFile != null) {
                // Get the PSI directory
              val psiDirectory = readAction {
                PsiManager.getInstance(project).findDirectory(virtualFile)
              }
              if (psiDirectory != null) {
                return selectInProjectView(psiDirectory)
              }
            }
        }
        return false
    }

    private suspend fun navigateToModuleDependency(userObject: ModulesTreeModelService.ModuleDependencyNode): Boolean {
        // First try to find the XML file for the module dependency
        val dependencyModule = userObject.dependency.module
        if (dependencyModule != null) {
            // Try to find module.xml file in the module's content roots
            val moduleXmlFile = findModuleXmlFile(dependencyModule)
            if (moduleXmlFile != null) {
                val virtualFile = getVirtualFile(moduleXmlFile.absolutePath)
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    return true
                }
            }

            // If no XML file found, navigate to the module in the tree as before
            val moduleNode = findModuleNodeByName(dependencyModule.name)
            if (moduleNode != null) {
                return selectAndScrollToNode(moduleNode)
            }
        }
        return true
    }

    /**
     * Finds the module.xml file for the given module.
     */
    private fun findModuleXmlFile(module: org.jetbrains.jps.model.module.JpsModule): File? {
        // Get the module directory
        val moduleDir = module.contentRootsList.urls.firstOrNull()?.removePrefix("file://")?.let { File(it) }
        if (moduleDir != null && moduleDir.exists()) {
            // Look for module.xml in META-INF directory
            val metaInfDir = File(moduleDir, "META-INF")
            if (metaInfDir.exists()) {
                val moduleXml = File(metaInfDir, "module.xml")
                if (moduleXml.exists()) {
                    return moduleXml
                }
            }
        }
        return null
    }

    private suspend fun navigateToXmlFile(userObject: Any): Boolean {
        // Get the file and navigate to it in the project view
        val file = when (userObject) {
            is ModulesTreeModelService.PluginXmlFileWithInfo -> userObject.file
            is ModulesTreeModelService.ModuleXmlFileWithInfo -> userObject.file
            is JpsModulesService.PluginXmlFile -> userObject.file
            is JpsModulesService.ModuleXmlFile -> userObject.file
            else -> null
        }

        if (file != null) {
            val virtualFile = getVirtualFile(file.absolutePath)
            if (virtualFile != null) {
              FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
        return false
    }

    private suspend fun navigateToOldFashionDependency(userObject: ModulesTreeModelService.OldFashionDependencyNode): Boolean {
        // Navigate to the old fashion dependency if possible
        // Check if there's a config file to navigate to
        val psiFile = readAction {
          val configFile = userObject.oldFashionDependency.configFile
          if (configFile != null) {
            val file = File(configFile)
            val virtualFile = getVirtualFile(file.absolutePath)
            if (virtualFile != null) {
              // Get the PSI file
                PsiManager.getInstance(project).findFile(virtualFile)
            } else null
          } else null
        }
        if (psiFile != null) {
          return selectInProjectView(psiFile)
        }
      return false
    }

    private suspend fun navigateToContentModule(userObject: ModulesTreeModelService.ContentModuleNode): Boolean {
        // Navigate to the module in the modules tree
        val contentModuleName = userObject.contentModule.name
        if (contentModuleName.isNotEmpty()) {
            val moduleNode = findModuleNodeByName(contentModuleName)
            // Select the node if found
            if (moduleNode != null) {
                return selectAndScrollToNode(moduleNode)
            }
        }
        return true
    }

    /**
     * Navigates to the plugin.xml file for the dependency plugin.
     */
    private suspend fun navigateToDependencyPlugin(userObject: ModulesTreeModelService.DependencyPluginNode): Boolean {
        // Find the plugin.xml file for the dependency plugin
        // First, we need to find the parent node which should be the XML file node
        val path = tree.selectionPath ?: return false
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
        val parentNode = node.parent as? DefaultMutableTreeNode ?: return false
        val grandParentNode = parentNode.parent as? DefaultMutableTreeNode ?: return false

        // The grand parent node should be the XML file node
        val xmlFileNode = grandParentNode
        val xmlFileObject = xmlFileNode.userObject

        // Get the XML file and navigate to it
        val file = when (xmlFileObject) {
            is ModulesTreeModelService.PluginXmlFileWithInfo -> xmlFileObject.file
            is ModulesTreeModelService.ModuleXmlFileWithInfo -> xmlFileObject.file
            is JpsModulesService.PluginXmlFile -> xmlFileObject.file
            is JpsModulesService.ModuleXmlFile -> xmlFileObject.file
            else -> null
        }

        if (file != null) {
            val virtualFile = getVirtualFile(file.absolutePath)
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
                return true
            }
        }

        return false
    }

  /**
     * Registers F4 key shortcut for navigating to the selected node.
     */
    fun registerF4KeyShortcut(coroutineScope: CoroutineScope) {
        val navigateAction = object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
              coroutineScope.launch {
                navigateToSelectedNode()
              }
            }
        }
        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0)
        navigateAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), tree)
    }

    /**
     * Finds a module node in the tree by name.
     * Returns a pair of (found node, was module already visible)
     */
    private suspend fun findModuleNodeByName(moduleName: String): DefaultMutableTreeNode? {
        val rootNode = tree.model.root as DefaultMutableTreeNode
        var moduleNode: DefaultMutableTreeNode? = null

        // First try to find the module in the current tree
        moduleNode = findModuleNodeInTree(rootNode, moduleName)

        // If not found, reset filter and try again
        if (moduleNode == null) {
            resetFilterIfNeeded(moduleName)
            delay(300)
            // After resetting, find the node again
            moduleNode = findModuleNodeInTree(rootNode, moduleName)
        }

        return moduleNode
    }

    /**
     * Finds a module node in the tree by name.
     * Returns the found node or null if not found.
     */
    private fun findModuleNodeInTree(rootNode: DefaultMutableTreeNode, moduleName: String): DefaultMutableTreeNode? {
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(i) as DefaultMutableTreeNode
            val currentModuleName = (node.userObject as? org.jetbrains.jps.model.module.JpsModule)?.name
            if (currentModuleName == moduleName) {
                return node
            }
        }
        return null
    }

    /**
     * Selects and scrolls to the specified node in the tree.
     */
    private suspend fun selectAndScrollToNode(node: DefaultMutableTreeNode): Boolean {
        val path = TreePath(node.path)
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
        return true
    }

    /**
     * Converts a file path to a VirtualFile.
     */
    private fun getVirtualFile(filePath: String): VirtualFile? {
        return VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
    }

    /**
     * Selects an item in the project view.
     */
    private suspend fun selectInProjectView(element: PsiElement): Boolean {
        val selectInManager = SelectInManager.getInstance(project)
        val context = when (element) {
            is com.intellij.psi.PsiFile -> FileSelectInContext(project, element.virtualFile)
            is com.intellij.psi.PsiDirectory -> FileSelectInContext(element)
            else -> return false
        }

        for (target in selectInManager.targetList) {
          if (readAction { target.canSelect(context) }) {
            target.selectIn(context, true)
            break
          }
          true
        }
        return false
    }
}
