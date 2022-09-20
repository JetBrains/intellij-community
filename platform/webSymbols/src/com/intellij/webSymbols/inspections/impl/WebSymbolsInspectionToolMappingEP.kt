package com.intellij.webSymbols.inspections.impl

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.WebSymbolReferenceProblem
import com.intellij.webSymbols.WebSymbolsContainer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
internal class WebSymbolsInspectionToolMappingEP : PluginAware {

  companion object {

    fun get(symbolNamespace: WebSymbolsContainer.Namespace,
            symbolKind: String,
            problemKind: WebSymbolReferenceProblem.ProblemKind): WebSymbolsInspectionToolMappingEP? =
      map.value[ExtensionKey(symbolNamespace, symbolKind, problemKind)]

  }

  @Attribute("symbolNamespace")
  @RequiredElement
  @JvmField
  var symbolNamespace: WebSymbolsContainer.Namespace? = null

  @Attribute("symbolKind")
  @RequiredElement
  @JvmField
  var symbolKind: String? = null

  @Attribute("problemKind")
  @RequiredElement
  @JvmField
  var problemKind: WebSymbolReferenceProblem.ProblemKind? = null

  @Attribute("toolShortName")
  @JvmField
  var toolShortName: String? = null

  @Attribute("bundleName")
  @JvmField
  var bundleName: String? = null

  @Attribute("messageKey")
  @JvmField
  var messageKey: String? = null

  private lateinit var pluginDescriptor: PluginDescriptor

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  @InspectionMessage
  fun getProblemMessage(symbolKindName: String?): String? {
    return getLocalizedString(messageKey ?: return null,
                              symbolKindName ?: WebSymbolsBundle.message("web.inspection.message.segment.default-subject"))
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
  var symbolNamespace: WebSymbolsContainer.Namespace,
  var symbolKind: String,
  var problemKind: WebSymbolReferenceProblem.ProblemKind,
)

private val EP_NAME = ExtensionPointName<WebSymbolsInspectionToolMappingEP>("com.intellij.javascript.web.inspectionToolMapping")

private val map: ClearableLazyValue<Map<ExtensionKey, WebSymbolsInspectionToolMappingEP>> = ExtensionPointUtil.dropLazyValueOnChange(
  ClearableLazyValue.create {
    EP_NAME.extensionList.associateBy { ext ->
      ExtensionKey(
        ext.symbolNamespace!!,
        ext.symbolKind!!,
        ext.problemKind!!)
    }
  }, EP_NAME, null
)