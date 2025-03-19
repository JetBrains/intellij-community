// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PluginXmlReader")
package com.intellij.platform.runtime.product.serialization.impl

import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import java.io.IOException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

private const val PLUGIN_XML_PATH = "META-INF/plugin.xml"

fun loadPluginModules(
  mainModule: RuntimeModuleDescriptor, repository: RuntimeModuleRepository,
  resourceFileResolver: ResourceFileResolver
): List<RawIncludedRuntimeModule> {
  try {
    val modules = ArrayList<RawIncludedRuntimeModule>()
    val addedModules = HashSet<String>()
    modules.add(RawIncludedRuntimeModule(mainModule.moduleId, RuntimeModuleLoadingRule.REQUIRED))
    addedModules.add(mainModule.moduleId.stringId)
    resourceFileResolver.readResourceFile(mainModule.moduleId, PLUGIN_XML_PATH).use { inputStream ->
      if (inputStream == null) {
        throw MalformedRepositoryException("$PLUGIN_XML_PATH is not found in '${mainModule.moduleId.stringId}' module in $repository " +
                                           "using $resourceFileResolver; resources roots: ${mainModule.resourceRootPaths}")
      }
      val reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream)
      var level = 0
      var inContentTag = false
      while (reader.hasNext()) {
        val event = reader.next()
        if (event == XMLStreamConstants.START_ELEMENT) {
          level++
          val tagName = reader.localName
          if (level == 2 && tagName == "content") {
            inContentTag = true
          }
          else if (level == 3 && inContentTag && tagName == "module") {
            var nameAttribute: String? = null
            var loading: String? = null
            for (i in 0 until  reader.attributeCount) {
              when (reader.getAttributeLocalName(i)) {
                "name" -> nameAttribute = reader.getAttributeValue(i)
                "loading" -> loading = reader.getAttributeValue(i)
              }
            }
            if (nameAttribute == null) {
              throw XMLStreamException("'name' attribute is not found in 'module' tag")
            }
            val moduleName = nameAttribute.substringBefore('/')
            if (addedModules.add(moduleName)) {
              val loadingRule = when (loading) {
                "required", "embedded" -> RuntimeModuleLoadingRule.REQUIRED
                "on-demand" -> RuntimeModuleLoadingRule.ON_DEMAND
                else -> RuntimeModuleLoadingRule.OPTIONAL
              }    
              modules.add(RawIncludedRuntimeModule(RuntimeModuleId.raw(moduleName), loadingRule))
            }
          }
        }
        else if (event == XMLStreamConstants.END_ELEMENT) {
          level--
          if (level == 0 || level == 1 && inContentTag) {
            break
          }
        }
      }
    }
    return modules
  }
  catch (e: IOException) {
    throw MalformedRepositoryException("Failed to load included modules for ${mainModule.moduleId.stringId}", e)
  }
  catch (e: XMLStreamException) {
    throw MalformedRepositoryException("Failed to load included modules for ${mainModule.moduleId.stringId}", e)
  }
}
