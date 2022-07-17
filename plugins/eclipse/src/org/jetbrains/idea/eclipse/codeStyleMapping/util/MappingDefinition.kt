// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeImportException

sealed class MappingDefinitionElement {
  data class IdToSettingMapping(val id: String, val settingMapping: SettingMapping<String>)
    : MappingDefinitionElement()

  data class OnImportAction(val action: () -> Unit)
    : MappingDefinitionElement()
}

open class MappingDefinitionBuilder {
  private val elements = mutableListOf<MappingDefinitionElement>()

  open fun preprocessId(id: String) = id

  infix fun String.mapTo(settingMapping: SettingMapping<String>) {
    elements.add(MappingDefinitionElement.IdToSettingMapping(preprocessId(this), settingMapping))
  }

  fun onImportDo(block: () -> Unit) {
    elements.add(MappingDefinitionElement.OnImportAction(block))
  }

  fun build() = MappingDefinition(elements)
}

class MappingDefinition(val elements: List<MappingDefinitionElement>) {
  fun exportSettings(): List<Pair<String, String>> = elements
    .filterIsInstance<MappingDefinitionElement.IdToSettingMapping>()
    .filter { it.settingMapping.isExportAllowed }
    .map { it.id to it.settingMapping.export() }

  /** Imports are performed in the order in which they appear in [elements] */
  fun importSettings(external: Map<String, String>) {
    for (elem in elements) {
      when (elem) {
        is MappingDefinitionElement.IdToSettingMapping -> {
          if (!elem.settingMapping.isImportAllowed) continue
          val externalValue = external[elem.id] ?: continue
          try {
            elem.settingMapping.import(externalValue)
          }
          catch (e: UnexpectedIncomingValue) {
            LOG.debug(e)
            throw SchemeImportException("Unexpected value when importing option ${elem.id}: ${e.value}")
          }
        }
        is MappingDefinitionElement.OnImportAction -> {
          elem.action()
        }
      }
    }
  }

  companion object {
    private val LOG = logger<MappingDefinition>()
  }
}