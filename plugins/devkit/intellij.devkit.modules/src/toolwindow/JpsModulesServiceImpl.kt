// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.File
import java.nio.file.Paths

/**
 * Implementation of JpsModulesService that loads JPS modules using the loadJpsModel function.
 */
class JpsModulesServiceImpl(private val project: Project) : JpsModulesService {
    private val LOG = logger<JpsModulesServiceImpl>()

    override fun getAllModules(): List<JpsModule> {
        try {
            // Get the project path
            val projectPath = Paths.get(project.basePath ?: "")

            // Load JPS model
            val jpsModel = loadJpsModel(projectPath)

            // Get all modules and sort them by name
            return jpsModel.project.modules.sortedBy { it.name }
        } catch (e: Exception) {
            LOG.warn("Error loading JPS modules", e)
            return emptyList()
        }
    }

    override fun findXmlFilesInModule(module: JpsModule): List<JpsModulesService.XmlFileInfo> {
        val xmlFiles = mutableListOf<JpsModulesService.XmlFileInfo>()
        val moduleName = module.name

        // Check all resource roots
        for (resourceRoot in module.getSourceRoots(org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE)) {
            val rootUrl = resourceRoot.url
            if (!rootUrl.startsWith("file://")) continue

            val rootPath = Paths.get(rootUrl.substring(7))
            val rootFile = rootPath.toFile()
            if (!rootFile.exists() || !rootFile.isDirectory) continue

            // Check for META-INF/plugin.xml
            val metaInfDir = File(rootFile, "META-INF")
            if (metaInfDir.exists() && metaInfDir.isDirectory) {
                val pluginXmlFile = File(metaInfDir, "plugin.xml")
                if (pluginXmlFile.exists() && pluginXmlFile.isFile) {
                    xmlFiles.add(JpsModulesService.PluginXmlFile(pluginXmlFile))
                }
            }

            // Check for <module-name>.xml
            val moduleXmlFile = File(rootFile, "$moduleName.xml")
            if (moduleXmlFile.exists() && moduleXmlFile.isFile) {
                xmlFiles.add(JpsModulesService.ModuleXmlFile(moduleXmlFile))
            }
        }

        return xmlFiles
    }
}
