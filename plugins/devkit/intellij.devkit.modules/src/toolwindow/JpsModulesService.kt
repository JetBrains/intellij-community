// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.project.Project
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.File

/**
 * Service for loading and providing JPS modules data.
 */
interface JpsModulesService {
    /**
     * Gets all JPS modules for the project, sorted by name.
     *
     * @return List of JPS modules sorted by name
     */
    fun getAllModules(): List<JpsModule>

    /**
     * Finds plugin.xml and module XML files in a module.
     *
     * @param module The module to search in
     * @return List of XML files found in the module's resource roots
     */
    fun findXmlFilesInModule(module: JpsModule): List<XmlFileInfo>

    /**
     * Data class representing an XML file found in a resource root.
     */
    sealed class XmlFileInfo {
        /**
         * The file object.
         */
        abstract val file: File
    }

    /**
     * Data class representing a plugin.xml file.
     */
    data class PluginXmlFile(override val file: File) : XmlFileInfo()

    /**
     * Data class representing a module XML file.
     */
    data class ModuleXmlFile(override val file: File) : XmlFileInfo()

    companion object {
        /**
         * Gets the JpsModulesService instance for the specified project.
         *
         * @param project The project
         * @return The JpsModulesService instance
         */
        @JvmStatic
        fun getInstance(project: Project): JpsModulesService {
            return project.getService(JpsModulesService::class.java)
        }
    }
}
