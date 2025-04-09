// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.jps.model.module.JpsModule
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JList
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Action for finding usages of a module tree node.
 */
class ModulesFindUsagesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? Tree ?: return
        val path = component.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject ?: return

        // Find usages of the selected node
        val usages = findUsages(project, userObject)

        // Show the usages in a popup
        showUsagesPopup(e, usages)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? Tree
        val path = component?.selectionPath
        val node = path?.lastPathComponent as? DefaultMutableTreeNode
        val userObject = node?.userObject

        // Enable the action only if a node is selected
        e.presentation.isEnabled = userObject != null
    }

    /**
     * Finds usages of the specified module tree node.
     */
    private fun findUsages(project: Project, userObject: Any): List<UsageInfo> {
        val jpsModulesService = JpsModulesService.getInstance(project)
        val xmlParserService = XmlParserService.getInstance(project)

        val usages = mutableListOf<UsageInfo>()

        when (userObject) {
            is JpsModule -> {
                // Find usages of the module in other modules' dependencies
                val allModules = jpsModulesService.getAllModules()
                for (module in allModules) {
                    for (dependency in module.dependenciesList.dependencies) {
                        if (dependency is org.jetbrains.jps.model.module.JpsModuleDependency && 
                            dependency.module?.name == userObject.name) {
                            usages.add(UsageInfo(
                                "Module Dependency in ${module.name}",
                                "Module ${userObject.name} is used as a dependency in ${module.name}",
                                module
                            ))
                        }
                    }
                }

                // Find usages in XML files
                for (module in allModules) {
                    val xmlFiles = jpsModulesService.findXmlFilesInModule(module)
                    for (xmlFile in xmlFiles) {
                        when (xmlFile) {
                            is JpsModulesService.PluginXmlFile -> {
                                val pluginInfo = xmlParserService.parsePluginXml(xmlFile.file)
                                if (pluginInfo != null) {
                                    // Check content modules
                                    for (contentModule in pluginInfo.contentModules) {
                                        if (contentModule.name == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Content Module in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is used as a content module in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check module values
                                    for (moduleValue in pluginInfo.modules) {
                                        if (moduleValue.value == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Module Value in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as a module value in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check dependency plugins
                                    for (dependencyPlugin in pluginInfo.dependencyPlugins) {
                                        if (dependencyPlugin.id == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Dependency Plugin in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as a dependency plugin in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check old-fashion dependencies
                                    for (oldFashionDependency in pluginInfo.oldFashionDependencies) {
                                        if (oldFashionDependency.id == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Old-Fashion Dependency in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as an old-fashion dependency in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }
                                }
                            }
                            is JpsModulesService.ModuleXmlFile -> {
                                val moduleInfo = xmlParserService.parseModuleXml(xmlFile.file)
                                if (moduleInfo != null) {
                                    // Check content modules
                                    for (contentModule in moduleInfo.contentModules) {
                                        if (contentModule.name == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Content Module in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is used as a content module in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check module values
                                    for (moduleValue in moduleInfo.modules) {
                                        if (moduleValue.value == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Module Value in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as a module value in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check dependency plugins
                                    for (dependencyPlugin in moduleInfo.dependencyPlugins) {
                                        if (dependencyPlugin.id == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Dependency Plugin in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as a dependency plugin in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }

                                    // Check old-fashion dependencies
                                    for (oldFashionDependency in moduleInfo.oldFashionDependencies) {
                                        if (oldFashionDependency.id == userObject.name) {
                                            usages.add(UsageInfo(
                                                "Old-Fashion Dependency in ${xmlFile.file.name}",
                                                "Module ${userObject.name} is referenced as an old-fashion dependency in ${xmlFile.file.name}",
                                                xmlFile.file
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ModulesTreeModelService.ModuleDependencyNode -> {
                val dependencyModule = userObject.dependency.module
                if (dependencyModule != null) {
                    // Find modules that depend on this module
                    val allModules = jpsModulesService.getAllModules()
                    for (module in allModules) {
                        if (module.name == dependencyModule.name) {
                            usages.add(UsageInfo(
                                "Module ${module.name}",
                                "This dependency refers to module ${module.name}",
                                module
                            ))
                        }
                    }
                }
            }
            is ModulesTreeModelService.ContentModuleNode -> {
                // Find the module that this content module refers to
                val allModules = jpsModulesService.getAllModules()
                for (module in allModules) {
                    if (module.name == userObject.contentModule.name) {
                        val description = if (userObject.pluginModuleName != null) {
                            "This content module refers to module ${module.name} (plugin: ${userObject.pluginModuleName})"
                        } else {
                            "This content module refers to module ${module.name}"
                        }
                        usages.add(UsageInfo(
                            "Module ${module.name}",
                            description,
                            module
                        ))
                    }
                }
            }
            is ModulesTreeModelService.PluginXmlFileWithInfo -> {
                // Find usages of this plugin.xml file
                val file = userObject.file
                val pluginInfo = userObject.pluginInfo
                if (pluginInfo != null) {
                    // Add information about the plugin
                    usages.add(UsageInfo(
                        "Plugin ID: ${pluginInfo.id ?: "Unknown"}",
                        "Plugin XML file: ${file.name}",
                        file
                    ))

                    // Add information about content modules
                    for (contentModule in pluginInfo.contentModules) {
                        val pluginId = pluginInfo.id ?: file.name
                        usages.add(UsageInfo(
                            "Content Module: ${contentModule.name}",
                            "Loading: ${contentModule.loading}, Plugin: $pluginId",
                            contentModule
                        ))
                    }

                    // Add information about dependencies
                    for (dependencyPlugin in pluginInfo.dependencyPlugins) {
                        usages.add(UsageInfo(
                            "Dependency Plugin: ${dependencyPlugin.id}",
                            "Plugin depends on: ${dependencyPlugin.id}",
                            dependencyPlugin
                        ))
                    }

                    // Add information about old-fashion dependencies
                    for (oldFashionDependency in pluginInfo.oldFashionDependencies) {
                        usages.add(UsageInfo(
                            "Old-Fashion Dependency: ${oldFashionDependency.id}",
                            "Optional: ${oldFashionDependency.optional}, Config File: ${oldFashionDependency.configFile ?: "None"}",
                            oldFashionDependency
                        ))
                    }
                }
            }
            is ModulesTreeModelService.ModuleXmlFileWithInfo -> {
                // Find usages of this module.xml file
                val file = userObject.file
                val moduleInfo = userObject.moduleInfo
                if (moduleInfo != null) {
                    // Add information about the module
                    usages.add(UsageInfo(
                        "Module ID: ${moduleInfo.id ?: "Unknown"}",
                        "Module XML file: ${file.name}",
                        file
                    ))

                    // Add information about content modules
                    for (contentModule in moduleInfo.contentModules) {
                        val moduleId = moduleInfo.id ?: file.name
                        usages.add(UsageInfo(
                            "Content Module: ${contentModule.name}",
                            "Loading: ${contentModule.loading}, Module: $moduleId",
                            contentModule
                        ))
                    }

                    // Add information about dependencies
                    for (dependencyPlugin in moduleInfo.dependencyPlugins) {
                        usages.add(UsageInfo(
                            "Dependency Plugin: ${dependencyPlugin.id}",
                            "Module depends on: ${dependencyPlugin.id}",
                            dependencyPlugin
                        ))
                    }
                }
            }
            else -> {
                // For other types of nodes, just return a generic message
                usages.add(UsageInfo(
                    "Node Information",
                    "Type: ${userObject.javaClass.simpleName}",
                    userObject
                ))
            }
        }

        // If no usages found, add a message
        if (usages.isEmpty()) {
            usages.add(UsageInfo(
                "No Usages Found",
                "No usages found for ${userObject}",
                userObject
            ))
        }

        return usages
    }

    /**
     * Shows the usages in a popup.
     */
    private fun showUsagesPopup(e: AnActionEvent, usages: List<UsageInfo>) {
        val project = e.project ?: return
        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return
        val inputEvent = e.inputEvent

        // Create a popup with the usages
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(usages)
            .setTitle("Usages")
            .setRenderer(UsageInfoRenderer())
            .setItemChosenCallback { usage ->
                // Navigate to the usage when selected
                when (val reference = usage.reference) {
                    is File -> {
                        // Open the file in the editor
                        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://${reference.absolutePath}")
                        if (virtualFile != null) {
                            FileEditorManager.getInstance(project).openFile(virtualFile, true)
                        }
                    }
                    is JpsModule -> {
                        // Show information about the module
                        JBPopupFactory.getInstance()
                            .createMessage("Module: ${reference.name}")
                            .showInBestPositionFor(e.dataContext)
                    }
                    else -> {
                        // Show the usage title
                        JBPopupFactory.getInstance()
                            .createMessage("Selected usage: ${usage.title}")
                            .showInBestPositionFor(e.dataContext)
                    }
                }
            }
            .createPopup()

        // Show the popup
        if (inputEvent is MouseEvent) {
            popup.show(RelativePoint(inputEvent))
        } else {
            popup.showInBestPositionFor(e.dataContext)
        }
    }

    /**
     * Data class representing a usage of a module tree node.
     */
    data class UsageInfo(
        val title: String,
        val description: String,
        val reference: Any? = null
    )

    /**
     * Custom renderer for UsageInfo items in the popup.
     */
    private inner class UsageInfoRenderer : ColoredListCellRenderer<UsageInfo>() {
        override fun customizeCellRenderer(
            list: JList<out UsageInfo>,
            value: UsageInfo,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            // Set the appropriate icon based on the reference type
            when (val reference = value.reference) {
                is File -> {
                    icon = AllIcons.FileTypes.Xml
                }
                is JpsModule -> {
                    icon = AllIcons.Nodes.Module
                }
                is XmlParserService.ContentModuleInfo -> {
                    icon = AllIcons.Nodes.Module
                }
                is XmlParserService.DependencyPluginInfo -> {
                    icon = AllIcons.Nodes.Plugin
                }
                is XmlParserService.OldFashionDependencyInfo -> {
                    icon = AllIcons.Nodes.Plugin
                }
                is XmlParserService.ModuleValueInfo -> {
                    icon = AllIcons.Nodes.Module
                }
                is ModulesTreeModelService.ModuleDependencyNode -> {
                    icon = AllIcons.Nodes.Module
                }
                else -> {
                    icon = AllIcons.General.Information
                }
            }

            // Add the title with bold text
            append(value.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)

            // Add the description with grayed text if it's not too long
            if (value.description.length < 50) {
                append(" - ${value.description}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
