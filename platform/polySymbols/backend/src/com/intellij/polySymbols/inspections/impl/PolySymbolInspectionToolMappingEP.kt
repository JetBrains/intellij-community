// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.inspections.impl

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolsBundle
import com.intellij.polySymbols.references.PolySymbolReferenceProblem.ProblemKind
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.Nls

internal class PolySymbolInspectionToolMappingEP : PluginAware {

  companion object {

    fun get(
      symbolNamespace: PolySymbolNamespace,
      symbolKind: String,
      problemKind: ProblemKind,
    ): PolySymbolInspectionToolMappingEP? =
      map.value[ExtensionKey(symbolNamespace, symbolKind, problemKind)]

  }

  @Attribute("symbolNamespace")
  @RequiredElement
  @JvmField
  var symbolNamespace: PolySymbolNamespace? = null

  @Attribute("symbolKind")
  @RequiredElement
  @JvmField
  var symbolKind: String? = null

  @Attribute("problemKind")
  @RequiredElement
  @JvmField
  var problemKind: ProblemKind? = null

  @Attribute("toolShortName")
  @JvmField
  var toolShortName: String? = null

  @Attribute("bundleName")
  @JvmField
  var bundleName: String? = null

  @Attribute("messageKey")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @JvmField
  var messageKey: String? = null

  private lateinit var pluginDescriptor: PluginDescriptor

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  @InspectionMessage
  fun getProblemMessage(symbolKindName: String?): String? {
    return getLocalizedString(messageKey ?: return null,
                              symbolKindName ?: PolySymbolsBundle.message("web.inspection.message.segment.default-subject"))
  }

  @Nls
  private fun getLocalizedString(key: String, vararg params: Any): String? {
    val baseName = bundleName
                   ?: pluginDescriptor.resourceBundleBaseName
                   ?: return null
    val resourceBundle = DynamicBundle.getResourceBundle(pluginDescriptor.classLoader, baseName)
    return AbstractBundle.message(resourceBundle, key, *params)
  }

}

private data class ExtensionKey(
  var symbolNamespace: PolySymbolNamespace,
  var symbolKind: String,
  var problemKind: ProblemKind,
)

private val EP_NAME = ExtensionPointName<PolySymbolInspectionToolMappingEP>("com.intellij.polySymbols.inspectionToolMapping")

private val map: ClearableLazyValue<Map<ExtensionKey, PolySymbolInspectionToolMappingEP>> = ExtensionPointUtil.dropLazyValueOnChange(
  ClearableLazyValue.create {
    EP_NAME.extensionList.associateBy { ext ->
      ExtensionKey(
        ext.symbolNamespace!!,
        ext.symbolKind!!,
        ext.problemKind!!)
    }
  }, EP_NAME, null
)