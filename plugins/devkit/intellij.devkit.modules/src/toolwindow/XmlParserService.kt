// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Service for parsing XML files like module and plugin.xml and extracting information.
 */
interface XmlParserService {
    /**
     * Parses a plugin.xml file and extracts information.
     *
     * @param file The plugin.xml file
     * @return The parsed plugin information, or null if parsing failed
     */
    fun parsePluginXml(file: File): PluginInfo?

    /**
     * Parses a module XML file and extracts information.
     *
     * @param file The module XML file
     * @return The parsed module information, or null if parsing failed
     */
    fun parseModuleXml(file: File): ModuleInfo?

    /**
     * Data class representing parsed plugin information.
     */
    data class PluginInfo(
        val id: String?,
        val packagePrefix: String?,
        val contentModules: List<ContentModuleInfo>,
        val dependencyPlugins: List<DependencyPluginInfo>,
        val oldFashionDependencies: List<OldFashionDependencyInfo>,
        val modules: List<ModuleValueInfo>
    )

    /**
     * Data class representing a module in the content section.
     */
    data class ContentModuleInfo(
        val name: String,
        val loading: String
    )

    /**
     * Data class representing a plugin in the dependencies section.
     */
    data class DependencyPluginInfo(
        val id: String
    )

    /**
     * Data class representing an old-fashion dependency.
     */
    data class OldFashionDependencyInfo(
        val id: String,
        val optional: Boolean,
        val configFile: String?
    )

    /**
     * Data class representing a module value definition.
     */
    data class ModuleValueInfo(
        val value: String
    )

    /**
     * Data class representing parsed module information.
     */
    data class ModuleInfo(
        val id: String?,
        val packagePrefix: String?,
        val contentModules: List<ContentModuleInfo>,
        val dependencyPlugins: List<DependencyPluginInfo>,
        val oldFashionDependencies: List<OldFashionDependencyInfo>,
        val modules: List<ModuleValueInfo>
    )

    companion object {
        /**
         * Gets the XmlParserService instance for the specified project.
         *
         * @param project The project
         * @return The XmlParserService instance
         */
        @JvmStatic
        fun getInstance(project: Project): XmlParserService {
            return project.getService(XmlParserService::class.java)
        }
    }
}

/**
 * Implementation of XmlParserService that uses standard Java XML parsing APIs.
 */
class XmlParserServiceImpl(private val project: Project) : XmlParserService {
    private val LOG = logger<XmlParserServiceImpl>()

    override fun parsePluginXml(file: File): XmlParserService.PluginInfo? {
        try {
            val document = parseXmlFile(file) ?: return null
            return parsePluginDocument(document)
        } catch (e: Exception) {
            LOG.warn("Error parsing plugin.xml file: ${file.absolutePath}", e)
            return null
        }
    }

    override fun parseModuleXml(file: File): XmlParserService.ModuleInfo? {
        try {
            val document = parseXmlFile(file) ?: return null
            val pluginInfo = parsePluginDocument(document)
            return XmlParserService.ModuleInfo(
                id = pluginInfo.id,
                packagePrefix = pluginInfo.packagePrefix,
                contentModules = pluginInfo.contentModules,
                dependencyPlugins = pluginInfo.dependencyPlugins,
                oldFashionDependencies = pluginInfo.oldFashionDependencies,
                modules = pluginInfo.modules
            )
        } catch (e: Exception) {
            LOG.warn("Error parsing module XML file: ${file.absolutePath}", e)
            return null
        }
    }

    private fun parseXmlFile(file: File): Document? {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            return builder.parse(file)
        } catch (e: Exception) {
            LOG.warn("Error parsing XML file: ${file.absolutePath}", e)
            return null
        }
    }

    private fun parsePluginDocument(document: Document): XmlParserService.PluginInfo {
        val rootElement = document.documentElement

        // Extract plugin ID
        val id = getElementTextContent(rootElement, "id")

        // Extract package prefix
        val packagePrefix = rootElement.getAttribute("package")

        // Extract content modules
        val contentModules = parseContentModules(rootElement)

        // Extract dependency plugins
        val dependencyPlugins = parseDependencyPlugins(rootElement)

        // Extract old-fashion dependencies
        val oldFashionDependencies = parseOldFashionDependencies(rootElement)

        // Extract module value definitions
        val modules = parseModuleValues(rootElement)

        return XmlParserService.PluginInfo(
            id = id,
            packagePrefix = packagePrefix.takeIf { it.isNotEmpty() },
            contentModules = contentModules,
            dependencyPlugins = dependencyPlugins,
            oldFashionDependencies = oldFashionDependencies,
            modules = modules
        )
    }

    private fun parseContentModules(rootElement: Element): List<XmlParserService.ContentModuleInfo> {
        val result = mutableListOf<XmlParserService.ContentModuleInfo>()
        val contentElement = getFirstChildElement(rootElement, "content") ?: return result

        val moduleElements = getChildElements(contentElement, "module")
        for (moduleElement in moduleElements) {
            val name = moduleElement.getAttribute("name")
            if (name.isEmpty()) continue

            val loading = moduleElement.getAttribute("loading").takeIf { it.isNotEmpty() } ?: "required"
            result.add(XmlParserService.ContentModuleInfo(name, loading))
        }
        return result
    }

    private fun parseDependencyPlugins(rootElement: Element): List<XmlParserService.DependencyPluginInfo> {
        val result = mutableListOf<XmlParserService.DependencyPluginInfo>()
        val dependenciesElement = getFirstChildElement(rootElement, "dependencies") ?: return result

        val pluginElements = getChildElements(dependenciesElement, "plugin")
        for (pluginElement in pluginElements) {
            val id = pluginElement.getAttribute("id")
            if (id.isEmpty()) continue

            result.add(XmlParserService.DependencyPluginInfo(id))
        }
        return result
    }

    private fun parseOldFashionDependencies(rootElement: Element): List<XmlParserService.OldFashionDependencyInfo> {
        val result = mutableListOf<XmlParserService.OldFashionDependencyInfo>()
        val dependsElements = getChildElements(rootElement, "depends")

        for (dependsElement in dependsElements) {
            val id = dependsElement.textContent.trim()
            if (id.isEmpty()) continue

            val optional = dependsElement.getAttribute("optional").equals("true", ignoreCase = true)
            val configFile = dependsElement.getAttribute("config-file").takeIf { it.isNotEmpty() }
            result.add(XmlParserService.OldFashionDependencyInfo(id, optional, configFile))
        }
        return result
    }

    private fun parseModuleValues(rootElement: Element): List<XmlParserService.ModuleValueInfo> {
        val result = mutableListOf<XmlParserService.ModuleValueInfo>()
        val moduleElements = getChildElements(rootElement, "module")

        for (moduleElement in moduleElements) {
            val value = moduleElement.getAttribute("value")
            if (value.isEmpty()) continue

            result.add(XmlParserService.ModuleValueInfo(value))
        }
        return result
    }

    private fun getElementTextContent(element: Element, tagName: String): String? {
        val childElement = getFirstChildElement(element, tagName) ?: return null
        return childElement.textContent.trim().takeIf { it.isNotEmpty() }
    }

    private fun getFirstChildElement(element: Element, tagName: String): Element? {
        val nodeList = element.getElementsByTagName(tagName)
        if (nodeList.length == 0) return null
        return nodeList.item(0) as Element
    }

    private fun getChildElements(element: Element, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodeList = element.getElementsByTagName(tagName)
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element && node.parentNode == element) {
                result.add(node)
            }
        }
        return result
    }
}
