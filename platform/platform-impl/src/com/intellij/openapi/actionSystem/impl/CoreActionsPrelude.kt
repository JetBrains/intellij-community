// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.pluginSystem.parser.impl.RawPluginDescriptor
import com.intellij.platform.pluginSystem.parser.impl.ScopedElementsContainer
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.platform.pluginSystem.parser.impl.parsePluginXml
import com.intellij.util.xml.dom.NoOpXmlInterner
import com.intellij.util.xml.dom.XmlInterner
import java.io.InputStream

/**
 * The core action set files. The list position defines the registration order.
 * The order is load-bearing: the first registered action wins the keymap dispatch,
 * and PriorityEditorLangActions must precede the standard editor actions.
 *
 * To add a file, append its path here and to the `coreActionSetDescriptors` mirror
 * in `ModuleStructureValidator`. When the new file carries same-shortcut partners,
 * also extend the `CoreActionRegistrationOrderTest` pin test.
 * Each file stays actions-only and xi:include-free. [loadCoreActionElements] enforces both rules.
 */
internal val CORE_ACTION_SET_PATHS: List<String> = listOf(
  "idea/PriorityEditorLangActions.xml",
  "idea/PlatformActions.xml",
  "idea/ExecutionActions.xml",
  "idea/LangActions.xml",
)

/**
 * The core action elements that [ActionPluginRegistrar] registers before every descriptor element.
 * The first registered action wins the keymap dispatch, so the prelude keeps the core actions first.
 *
 * Do not generalize this mechanism to another plugin. The prelude bypasses the descriptor model,
 * so static tooling, `validateActionsCanBeUnloaded`, and a descriptor dump do not see its elements.
 * The core plugin is the single allowed carrier by design, and the guard below enforces that.
 */
internal class CoreActionsPrelude(
  @JvmField val coreModule: IdeaPluginDescriptorImpl,
  @JvmField val elements: List<ActionElement>,
) {
  init {
    require(coreModule.pluginId == PluginManagerCore.CORE_ID) {
      "The prelude bypasses the descriptor model and the unload validation, " +
      "so only the core plugin may carry it, not '${coreModule.pluginId}'"
    }
  }
}

/**
 * Loads the core action sets for the core plugin.
 * A per-product opt-out would plug in here if a product ever needs one. No product needs one today.
 */
internal fun loadCoreActionsPrelude(pluginSet: PluginSet): CoreActionsPrelude? {
  val coreModule = pluginSet.findEnabledPlugin(PluginManagerCore.CORE_ID)
  if (coreModule == null) {
    actionManagerImplLog.warn("The core plugin is not in the plugin set, so the core action sets are not loaded")
    return null
  }
  val classLoader = coreModule.pluginClassLoader ?: CoreActionsPrelude::class.java.classLoader
  return CoreActionsPrelude(coreModule = coreModule, elements = loadCoreActionElements(classLoader::getResourceAsStream))
}

internal fun loadCoreActionElements(resolve: (String) -> InputStream?): List<ActionElement> {
  return CORE_ACTION_SET_PATHS.flatMap { path ->
    val stream = resolve(path) ?: throw IllegalStateException("The core action set '$path' is not on the core plugin classpath")
    val descriptor = parsePluginXml(input = stream, locationSource = path, readContext = CoreActionSetReadContext, xIncludeLoader = null)
      .build()
    checkActionsOnly(descriptor, path)
    descriptor.actions
  }
}

private fun checkActionsOnly(descriptor: RawPluginDescriptor, path: String) {
  val offenders = buildList {
    if (descriptor.extensions.isNotEmpty()) add("extensions")
    if (descriptor.pluginAliases.isNotEmpty()) add("plugin aliases")
    if (descriptor.contentModules.isNotEmpty()) add("content modules")
    if (descriptor.depends.isNotEmpty() || descriptor.dependencies.isNotEmpty()) add("dependencies")
    if (descriptor.incompatibleWith.isNotEmpty()) add("incompatibilities")
    addScopeOffenders("application", descriptor.appElementsContainer)
    addScopeOffenders("project", descriptor.projectElementsContainer)
    addScopeOffenders("module", descriptor.moduleElementsContainer)
  }
  if (offenders.isNotEmpty()) {
    throw IllegalStateException("The core action set '$path' must carry only actions, but it carries: ${offenders.joinToString()}")
  }
}

private fun MutableList<String>.addScopeOffenders(scope: String, container: ScopedElementsContainer) {
  if (container.services.isNotEmpty()) add("$scope services")
  if (container.components.isNotEmpty()) add("$scope components")
  if (container.listeners.isNotEmpty()) add("$scope listeners")
  if (container.extensionPoints.isNotEmpty()) add("$scope extension points")
}

private object CoreActionSetReadContext : PluginDescriptorReaderContext {
  override val interner: XmlInterner = NoOpXmlInterner
  override val isMissingIncludeIgnored: Boolean = false
}
