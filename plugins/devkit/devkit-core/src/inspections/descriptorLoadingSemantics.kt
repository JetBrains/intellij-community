// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.module.Module
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomUtil
import com.intellij.xml.util.XmlUtil
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

/**
 * A module without an own content-module descriptor is packaged into the plugin's main jar, so its classes come from
 * the main classloader. A declared content module shares it only when every `<content>` entry including it says
 * `loading="embedded"`; any other loading mode means an own classloader. Zero found entries answer `false`: an
 * unindexed or slash-named sub-descriptor inclusion (`intellij.foo/backend`) must not silently widen the exemption.
 */
internal fun isLoadedByMainPluginClassloader(module: Module): Boolean {
  return mainClassloaderRelation(module) == MainClassloaderRelation.MAIN
}

/**
 * A module whose classes resolve main-classloader classes at runtime: it is loaded by the main classloader itself,
 * or it is an optional content module — those receive the main descriptor as an implicit runtime dependency, so the
 * main classloader becomes a parent of theirs. Required and on-demand modules get no such parent, except in the
 * core plugin, whose main classes come from the core loader that every module classloader sees.
 */
internal fun seesMainPluginClassloader(module: Module): Boolean {
  return mainClassloaderRelation(module) != MainClassloaderRelation.SEPARATE
}

private enum class MainClassloaderRelation {
  MAIN, IMPLICIT_PARENT, SEPARATE
}

/**
 * Aggregated over every `<content>` entry that includes the module, so products that package it must agree.
 * Cached because the entry lookup falls back to scanning all production XML files when the index has no hits.
 */
private fun mainClassloaderRelation(module: Module): MainClassloaderRelation {
  val project = module.project
  return CachedValuesManager.getManager(project).getCachedValue(module) {
    CachedValueProvider.Result.create(
      computeMainClassloaderRelation(module),
      *descriptorGraphDependencies(project),
    )
  }
}

private fun computeMainClassloaderRelation(module: Module): MainClassloaderRelation {
  val contentModuleDescriptor = PluginModuleType.getContentModuleDescriptorXml(module) ?: return MainClassloaderRelation.MAIN
  val entries = SplitModeInspectionUtil.findDependingContentModuleEntriesInFile(contentModuleDescriptor).toList()
  return when {
    entries.isEmpty() -> MainClassloaderRelation.SEPARATE
    entries.all { it.loading.value == ModuleLoadingRule.EMBEDDED } -> MainClassloaderRelation.MAIN
    entries.all { resolvesMainClassloaderClasses(it) } -> MainClassloaderRelation.IMPLICIT_PARENT
    else -> MainClassloaderRelation.SEPARATE
  }
}

/**
 * Whether a `<content>` entry gives the module a classloader that resolves main-classloader classes: optional
 * modules (absent `loading` means optional) receive the main descriptor as an implicit runtime dependency, and
 * every content module of the core plugin gets the core loader — which is what loads the core plugin's main classes.
 */
private fun resolvesMainClassloaderClasses(entry: ContentDescriptor.ModuleDescriptor): Boolean {
  val loading = entry.loading.value
  if (loading == null || loading == ModuleLoadingRule.EMBEDDED || loading == ModuleLoadingRule.OPTIONAL) return true
  return isPartOfCorePluginDescriptor(DomUtil.getFile(entry), HashSet())
}

/**
 * The entry's file may be an id-less fragment of the effective descriptor, so the owning plugin id is resolved
 * through the include graph: a file declaring an id answers directly; a fragment belongs to the core plugin only
 * when every production descriptor xi-including it does; a root without an own id may still take the id from a
 * fragment it includes (product descriptors include the id-carrying PlatformLangPlugin.xml). Only whole-descriptor
 * includes count either way: a sub-selecting `xpointer` carries neither the `<content>` entries nor the `<id>`.
 */
private fun isPartOfCorePluginDescriptor(file: XmlFile, currentPath: MutableSet<XmlFile>): Boolean {
  return visitOnce(currentPath, file, onCycle = false) {
    DescriptorUtil.getIdeaPlugin(file)?.pluginId?.let { return it == PluginManagerCore.CORE_PLUGIN_ID }
    val includers = findProductionXIncludeEdges(file).filter { it.mergesWholeDescriptor }.map { it.includer }
    if (includers.isNotEmpty()) return includers.all { isPartOfCorePluginDescriptor(it, currentPath) }
    resolveEffectivePluginId(file, HashSet()) == PluginManagerCore.CORE_PLUGIN_ID
  }
}

private fun resolveEffectivePluginId(file: XmlFile, visited: MutableSet<XmlFile>): String? {
  if (!visited.add(file)) return null
  DescriptorUtil.getIdeaPlugin(file)?.pluginId?.let { return it }
  val rootTag = file.rootTag ?: return null
  return PsiTreeUtil.getChildrenOfTypeAsList(rootTag, XmlTag::class.java)
    .filter { it.namespace == XmlUtil.XINCLUDE_URI && it.localName == "include" && mergesWholeDescriptor(it) }
    .firstNotNullOfOrNull { includeTag -> resolveXIncludeTargetFile(includeTag)?.let { resolveEffectivePluginId(it, visited) } }
}

/**
 * Modules listed in one `pluginsModules` set are assembled into a single plugin and share its registration scope, so
 * registered names (extension points, actions, groups) resolve between them without a JPS dependency edge. They do not
 * necessarily share a classloader: classes and bundles resolve between siblings only when both sides are loaded by the
 * plugin's main classloader — a content module included with `loading="embedded"` everywhere, or a module packaged
 * into the main jar without an own content-module descriptor; a non-embedded content module gets its own classloader.
 * [module] is not a sibling of itself: a reference inside one module is decided by that module's runtime scope, which
 * tells production and test roots apart.
 * A module may be listed in several sets when several plugins package it; two modules are siblings when at least one
 * set contains both.
 * Returns `false` when [PluginXmlRegistrationCheckInspection] is absent from the profile or declares no set containing [module].
 *
 * Module sets are read from the project's current profile on purpose, ignoring the profile of the running inspection
 * session (the batch wrapper set by `GlobalInspectionContextImpl.inspectFile`): they describe project composition, so
 * Inspect Code with another profile and Run Inspection by Name, whose profiles lack this inspection, keep the exemption.
 */
internal fun areSiblingModulesInSamePlugin(module: Module, otherModule: Module, context: PsiElement): Boolean {
  if (module == otherModule) return false
  val inspection = InspectionProjectProfileManager.getInstance(module.project).currentProfile
                     .getUnwrappedTool(REGISTRATION_CHECK_SHORT_NAME, context) as? PluginXmlRegistrationCheckInspection
                   ?: return false
  return inspection.areModulesInSamePluginSet(module.name, otherModule.name)
}

private const val REGISTRATION_CHECK_SHORT_NAME = "PluginXmlRegistrationCheck"

internal class DeclaredDependencyNames(val moduleNames: Set<String>, val pluginIds: Set<String>) {
  operator fun plus(other: DeclaredDependencyNames): DeclaredDependencyNames {
    return DeclaredDependencyNames(moduleNames + other.moduleNames, pluginIds + other.pluginIds)
  }

  fun intersect(other: DeclaredDependencyNames): DeclaredDependencyNames {
    return DeclaredDependencyNames(moduleNames.intersect(other.moduleNames), pluginIds.intersect(other.pluginIds))
  }
}

/**
 * Names enabled whenever [file] itself is loaded: its effective declared dependencies, plus — for a descriptor the
 * runtime only ever loads as a content module — the names required by every plugin packaging it, since the module
 * loads only inside one of them. Sound for registry reachability only, not for classloader questions: a content
 * module's classloader does not see its packaging plugin's dependencies. A file with any other production load
 * context (an xi-includer or a `<depends config-file>` declarer) keeps its declared names alone: in those contexts
 * no packaging plugin is guaranteed. The packager term stays non-recursive because a live packager is always a
 * plugin's main descriptor: the loader ignores a `<content>` block in a content-module descriptor (a warned no-op,
 * see [com.intellij.ide.plugins.ContentModuleDescriptor]), so a module content-listed only by another content module
 * never loads at all, and there is no deeper packaging chain to enable names through. Non-recursion also keeps
 * mutual-packaging cycles terminating.
 */
internal fun namesEnabledWhenLoaded(file: XmlFile): DeclaredDependencyNames {
  val declared = effectiveDeclaredDependencyNames(file)
  if (findProductionXIncludeEdges(file).isNotEmpty() || findProductionConfigFileDependsEdges(file).isNotEmpty()) return declared
  val packagerNames = filesWithContentEntryFor(file)
    .map { effectiveDeclaredDependencyNames(it) }
    .reduceOrNull(DeclaredDependencyNames::intersect) ?: return declared
  return declared + packagerNames
}

internal fun effectiveDeclaredDependencyNames(file: XmlFile): DeclaredDependencyNames {
  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(
      collectDependencyNamesVisibleInEveryContext(file, HashSet()),
      *descriptorGraphDependencies(file.project),
    )
  }
}

/**
 * Module names or plugin ids declared in `<dependencies>` that cover registrations in this file at runtime; the
 * plugin-id component also counts non-optional v1 `<depends>` entries, which gate the descriptor's load the same way,
 * while an optional `<depends>` gates only its `config-file` content and proves nothing about the declaring file.
 * The file may be merged into several descriptors via `xi:include` and the registration must resolve in each of them,
 * so a name counts only when every production include context sees it; test-source includers describe no production
 * classloader and are ignored. A file loaded on its own — a plugin's `META-INF/plugin.xml` or a content module
 * descriptor — is itself a runtime context that sees only its own declarations, so include contexts cannot add names
 * for it. An includer merging only a selected part of the file (a sub-selecting `xpointer`) leaves the file's own
 * `<dependencies>` behind, so only the includer's names count in that context.
 * A file referenced as a `<depends config-file="...">` sub-descriptor is a context of its own: the runtime loads it
 * only when the declaring descriptor is loaded AND the depends target plugin is enabled, so that context sees the
 * declaring file's names, the file's own names, and — in the plugin-id component — the depends target even when the
 * entry is optional.
 */
private fun collectDependencyNamesVisibleInEveryContext(file: XmlFile, currentPath: MutableSet<XmlFile>): DeclaredDependencyNames {
  return visitOnce(currentPath, file, onCycle = DeclaredDependencyNames(emptySet(), emptySet())) {
    val ownNames = DescriptorUtil.getIdeaPlugin(file)?.let { plugin ->
      DeclaredDependencyNames(
        moduleNames = plugin.dependencies.moduleEntry.mapNotNull { it.name.stringValue }.toHashSet(),
        pluginIds = (plugin.dependencies.plugin.mapNotNull { it.id.stringValue } +
                     plugin.depends.filter { it.optional.value != true }.mapNotNull { it.rawText ?: it.stringValue }).toHashSet(),
      )
    } ?: DeclaredDependencyNames(emptySet(), emptySet())
    val contexts = ArrayList<DeclaredDependencyNames>()
    if (isIndependentlyLoadedDescriptor(file)) {
      contexts.add(ownNames)
    }
    findProductionXIncludeEdges(file).mapTo(contexts) { edge ->
      val includerNames = collectDependencyNamesVisibleInEveryContext(edge.includer, currentPath)
      if (edge.mergesWholeDescriptor) ownNames + includerNames else includerNames
    }
    findProductionConfigFileDependsEdges(file).mapTo(contexts) { edge ->
      val declaringNames = collectDependencyNamesVisibleInEveryContext(edge.declaring, currentPath)
      val gateNames = DeclaredDependencyNames(moduleNames = emptySet(), pluginIds = setOfNotNull(edge.dependsPluginId))
      ownNames + declaringNames + gateNames
    }
    contexts.reduceOrNull { common, next -> common.intersect(next) } ?: ownNames
  }
}
