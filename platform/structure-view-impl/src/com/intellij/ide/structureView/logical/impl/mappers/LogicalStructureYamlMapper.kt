// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl.mappers

import com.intellij.ide.TypePresentationService
import com.intellij.ide.structureView.logical.PropertyElementProvider
import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.structureView.logical.model.ProvidedLogicalContainer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LogicalStructureYamlMapper {
  companion object {
    fun <T> map(model: LogicalStructureAssembledModel<T>): Map<String, Any> {
      val result = LinkedHashMap<String, Any>()

      val properties = HashMap<String, Any>()
      if (model.model is PsiFile) {
        properties["path"] = model.model.virtualFile.path
        properties.putAll(mapChildrenModels(model, HashSet()))
      }
      result["logical-structure"] = properties
      return result
    }

    private fun <T> logicalModel(model: LogicalStructureAssembledModel<T>, visited: HashSet<Any> = HashSet()): Pair<String, HashMap<String, Any>> {
      val dataModel = model.model
      val logicalModelMapperProvider = LogicalModelMapperProvider.getInstance(dataModel)

      visited.add(dataModel as Any)
      val type = if (dataModel is LogicalModelMapper)
        dataModel.type()
      else if (logicalModelMapperProvider != null)
        logicalModelMapperProvider.type(dataModel)
      else
        dataModel::class.java.simpleName

      val allProperties = LinkedHashMap<String, Any>()
      allProperties += if (dataModel is LogicalModelMapper)
        dataModel.attributes()
      else if (logicalModelMapperProvider != null)
        logicalModelMapperProvider.attributes(dataModel)
      else
        getDefaultAttributes(dataModel)

      if (!model.hasSameModelParent()) {
        allProperties.putAll(mapChildrenModels(model, visited))
      }

      return type to allProperties
    }

    private fun <T> getDefaultAttributes(t: T) : HashMap<String, Any> {
      val defaultAttributes = LinkedHashMap<String, Any>()
      val name = TypePresentationService.getService().getObjectName(t as Any)
      if (StringUtil.isNotEmpty(name)) defaultAttributes["name"] = name.orEmpty()
      val typeName = TypePresentationService.getService().getTypeName(t as Any)
      if (StringUtil.isNotEmpty(typeName)) defaultAttributes["type"] = typeName.orEmpty()

      return defaultAttributes
    }

    private fun <T> mapChildrenModels(model: LogicalStructureAssembledModel<T>, visited: HashSet<Any> ): LinkedHashMap<String, Any> {
      val allProperties = LinkedHashMap<String, Any>()
      for (assembledModel in model.getChildren()) {
        if (visited.contains(assembledModel.model) || assembledModel.hasSameModelParent()) continue
        if (assembledModel.model is ProvidedLogicalContainer<*>) {
          val assembled = assembledModel as LogicalStructureAssembledModel<ProvidedLogicalContainer<T>>
          if (assembledModel.model.provider is PropertyElementProvider) {
            mapPropertyElementProvider(assembled, visited)?.let { allProperties[it.first] = it.second }
          }
          else {
            mapLogicalContainerProperties(assembled, visited)?.let { allProperties[it.first] = it.second } ?: continue
          }
        } else {
          logicalModel(assembledModel, visited).let { allProperties[it.first] = it.second }
        }
      }
      return allProperties
    }

    private fun <T> mapLogicalContainerProperties(assembled: LogicalStructureAssembledModel<ProvidedLogicalContainer<T>>, visited: HashSet<Any>): Pair<String, HashMap<String, Any>>? {
      val containerName = TypePresentationService.getService().getObjectName(assembled.model.provider) ?: return null
      if (containerName == "Referenced by") return null  // TODO: SOE !!!
      val mappedItems = HashMap<String, Any>()

      val properties = MultiMap.create<String, Map<String, Any>>()
      assembled.getChildren().asSequence()
        .filter { !visited.contains(it.model) }
        .map { logicalModel(it, visited) }
        .forEach { (key, value) -> properties.putValue(key, value) }

      properties.entrySet().forEach { entry ->
        val values = entry.value
        if (values.size == 1) {
          mappedItems[entry.key] = values.first()
        } else {
          values.forEachIndexed { index, item -> mappedItems["${entry.key}-${index + 1}"] = item }
        }
      }

      return containerName to mappedItems
    }

    private fun <T> mapPropertyElementProvider(assembled: LogicalStructureAssembledModel<ProvidedLogicalContainer<T>>, visited: HashSet<Any>): Pair<String, Any>? {
      val provider = assembled.model.provider as? PropertyElementProvider ?: return null
      assembled.model.getElements()
      return provider.propertyName?.let { it to logicalModel(assembled, visited).second }

      //val propertyName = provider.propertyName ?: return null
      //val mappedItems = assembled.model.getElements().mapTo(HashSet()) { logicalModel(it, visited) }
      //return propertyName to mappedItems
    }
  }
}