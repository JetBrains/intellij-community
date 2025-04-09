// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import javax.swing.tree.DefaultTreeModel

/**
 * Service for managing the modules tree model.
 */
interface ModulesTreeModelService {
    /**
     * Updates the list of modules in the tree based on the current filter.
     *
     * @param prefix The prefix to filter modules by
     */
    suspend fun updateModulesList(prefix: String = "") : DefaultTreeModel

    /**
     * Node classes for the tree model.
     */
    class SourceRootNode(val sourceRoot: org.jetbrains.jps.model.module.JpsModuleSourceRoot)
    class ResourceRootNode(val sourceRoot: org.jetbrains.jps.model.module.JpsModuleSourceRoot)
    class TestRootNode(val sourceRoot: org.jetbrains.jps.model.module.JpsModuleSourceRoot)
    class TestResourceRootNode(val sourceRoot: org.jetbrains.jps.model.module.JpsModuleSourceRoot)
    class ModuleDependencyNode(val dependency: org.jetbrains.jps.model.module.JpsModuleDependency)
    class LibraryDependencyNode(val dependency: org.jetbrains.jps.model.module.JpsLibraryDependency)

    class PluginXmlFileWithInfo(val file: java.io.File, val pluginInfo: XmlParserService.PluginInfo?)
    class ModuleXmlFileWithInfo(val file: java.io.File, val moduleInfo: XmlParserService.ModuleInfo?)

    class ContentModuleNode(val contentModule: XmlParserService.ContentModuleInfo, val pluginModuleName: String? = null)
    class DependencyPluginNode(val dependencyPlugin: XmlParserService.DependencyPluginInfo)
    class OldFashionDependencyNode(val oldFashionDependency: XmlParserService.OldFashionDependencyInfo)
    class ModuleValueNode(val moduleValue: XmlParserService.ModuleValueInfo)

    companion object {
        /**
         * Gets the ModulesTreeModelService instance for the specified project.
         *
         * @param project The project
         * @return The ModulesTreeModelService instance
         */
        @JvmStatic
        fun getInstance(project: Project): ModulesTreeModelService {
            return project.getService(ModulesTreeModelService::class.java)
        }
    }
}
