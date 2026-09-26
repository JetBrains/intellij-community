// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomUtil
import com.intellij.xml.util.XmlUtil
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal fun isInProductionRoots(file: VirtualFile?, project: Project): Boolean {
  val fileIndex = ProjectFileIndex.getInstance(project)
  return file != null && fileIndex.isInSourceContent(file) && !fileIndex.isInTestSourceContent(file)
}

internal fun isInTestRoots(file: VirtualFile?, project: Project): Boolean {
  return file != null && ProjectFileIndex.getInstance(project).isInTestSourceContent(file)
}

/** Everything derived from the descriptor include/dependency graph depends on project roots and XML PSI. */
internal fun descriptorGraphDependencies(project: Project): Array<Any> {
  return arrayOf(
    ProjectRootModificationTracker.getInstance(project),
    PsiManager.getInstance(project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
  )
}

/**
 * A descriptor the runtime loads on its own: a plugin's `META-INF/plugin.xml` or a module descriptor referenced from
 * a production `<content>` entry. Descriptors xi-including such a file only reuse its content and do not relax what
 * its own load resolves.
 */
internal fun isIndependentlyLoadedDescriptor(file: XmlFile): Boolean {
  return file.name == "plugin.xml" && file.parent?.name == "META-INF" || isLoadedAsContentModule(file)
}

private fun isLoadedAsContentModule(file: XmlFile): Boolean {
  val virtualFile = file.virtualFile ?: return false
  return isDescriptorOfContainingJpsModule(file) &&
         PluginIdDependenciesIndex.findFilesIncludingContentModule(file.project, virtualFile).isNotEmpty()
}

/**
 * Whether the loader could pick [file] as a content-module descriptor: descriptors are located by module name, so
 * the basename must name the JPS module containing the file — `foo.xml` in module `foo`, or `foo.sub.xml` in module
 * `foo` for a `foo/sub` entry. A same-named file in an unrelated module is packaged wherever that module goes and
 * never loads as this content module. Accepted owner names mirror ModuleDescriptorNameConverter's Gradle
 * normalizations.
 */
internal fun isDescriptorOfContainingJpsModule(file: XmlFile): Boolean {
  val virtualFile = file.virtualFile ?: return false
  val module = ModuleUtilCore.findModuleForFile(virtualFile, file.project) ?: return false
  val name = virtualFile.nameWithoutExtension
  return acceptedOwnerModuleNames(module).any { name == it || name.startsWith("$it.") }
}

/**
 * Whether [file] is the descriptor a content-module name denotes: the name up to a `/` names the owning JPS module —
 * `foo.bar` lives in module `foo.bar`, `foo/bar` in module `foo`. Both spellings share the `foo.bar.xml` basename,
 * so only the owning module tells them apart; a same-named file elsewhere belongs to a different module name.
 */
internal fun isDescriptorOfDenotedJpsModule(file: XmlFile, moduleName: String): Boolean {
  val virtualFile = file.virtualFile ?: return false
  val module = ModuleUtilCore.findModuleForFile(virtualFile, file.project) ?: return false
  val ownerName = moduleName.substringBefore('/')
  return acceptedOwnerModuleNames(module).any { it == ownerName }
}

private fun acceptedOwnerModuleNames(module: Module): Sequence<String> {
  return sequenceOf(module.name, module.name.removeSuffix(".main"), module.name.replace('_', '.').removeSuffix(".main"))
}

/**
 * The descriptors whose `<content>` block has a module entry for [descriptor], looked up by target file name.
 * A file the loader could never pick for its own name has no content entries, whatever the index matched.
 */
internal fun filesWithContentEntryFor(descriptor: XmlFile): List<XmlFile> {
  val virtualFile = descriptor.virtualFile ?: return emptyList()
  if (!isDescriptorOfContainingJpsModule(descriptor)) return emptyList()
  return PluginIdDependenciesIndex.findFilesIncludingContentModule(descriptor.project, virtualFile)
    .mapNotNull { descriptor.manager.findFile(it) as? XmlFile }
}

internal data class ProductionXIncludeEdge(val includer: XmlFile, val mergesWholeDescriptor: Boolean)

/**
 * Cached per file: the reachability walks re-enter this for every file of the include graph on every checked
 * reference. Includer candidates come from [PluginIdDependenciesIndex] by target file name and are verified by
 * resolving their `xi:include` hrefs; a reference search would scan every text occurrence of the file name in the
 * project — for a file named `plugin.xml` that is most of the monorepo.
 */
internal fun findProductionXIncludeEdges(file: XmlFile): List<ProductionXIncludeEdge> {
  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(
      computeProductionXIncludeEdges(file),
      *descriptorGraphDependencies(file.project),
    )
  }
}

private fun computeProductionXIncludeEdges(file: XmlFile): List<ProductionXIncludeEdge> {
  val virtualFile = file.virtualFile ?: return emptyList()
  val candidates = PluginIdDependenciesIndex.findFilesWithXIncludeOf(file.project, virtualFile.name)
  return productionXmlFiles(candidates - virtualFile, file).flatMap { includer ->
    findXIncludeTags(includer)
      .filter { resolveXIncludeTargetFile(it)?.virtualFile == virtualFile }
      .map { ProductionXIncludeEdge(includer, mergesWholeDescriptor(it)) }
  }.distinct()
}

private fun findXIncludeTags(file: XmlFile): List<XmlTag> {
  val result = ArrayList<XmlTag>()
  fun visit(tag: XmlTag) {
    if (tag.namespace == XmlUtil.XINCLUDE_URI && tag.localName == "include") result.add(tag)
    // physical children: getSubTags substitutes the included content for every resolvable xi:include tag
    tag.children.forEach { if (it is XmlTag) visit(it) }
  }
  file.rootTag?.let(::visit)
  return result
}

internal fun resolveXIncludeTargetFile(includeTag: XmlTag): XmlFile? {
  val href = includeTag.getAttribute("href")?.valueElement ?: return null
  return href.references.maxByOrNull { it.rangeInElement.startOffset }?.resolve() as? XmlFile
}

/**
 * A production descriptor loading [declaring]'s `<depends config-file="...">` sub-descriptor, with the depends
 * entry's target plugin id.
 */
internal data class ConfigFileDependsEdge(val declaring: XmlFile, val dependsPluginId: String?)

internal fun findProductionConfigFileDependsEdges(file: XmlFile): List<ConfigFileDependsEdge> {
  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(
      computeProductionConfigFileDependsEdges(file),
      *descriptorGraphDependencies(file.project),
    )
  }
}

private fun computeProductionConfigFileDependsEdges(file: XmlFile): List<ConfigFileDependsEdge> {
  val virtualFile = file.virtualFile ?: return emptyList()
  val candidates = PluginIdDependenciesIndex.findFilesWithConfigFileDepends(file.project, virtualFile)
  return productionXmlFiles(candidates, file).flatMap { declaring ->
    DescriptorUtil.getIdeaPlugin(declaring)?.depends.orEmpty()
      .filter { it.resolvedConfigFile?.virtualFile == virtualFile }
      .map { ConfigFileDependsEdge(declaring, it.rawText ?: it.stringValue) }
  }.distinct()
}

private fun productionXmlFiles(candidates: Collection<VirtualFile>, context: XmlFile): List<XmlFile> {
  return candidates
    .filter { isInProductionRoots(it, context.project) }
    .mapNotNull { context.manager.findFile(it) as? XmlFile }
}

/**
 * A root-level `xi:include` without an `xpointer` merges the whole included descriptor, as does the whole-children
 * pointer selecting every child of the root tag. An include nested inside a section or a pointer descending into one
 * section merges that section only.
 */
internal fun mergesWholeDescriptor(includeTag: XmlTag): Boolean {
  if (includeTag.parentTag != (includeTag.containingFile as? XmlFile)?.rootTag) return false
  val xpointer = includeTag.getAttributeValue("xpointer") ?: return true
  val pointer = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer).takeIf { it.matches() }?.group(1) ?: return false
  val children = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
  return children.matches() && children.group(2) == null
}

internal enum class IncludeEdgeFilter {
  /**
   * Roots guaranteed to receive the full content of the walked files: only whole-descriptor `xi:include` edges are
   * followed, so a sub-selecting includer never counts as carrying a registration.
   */
  WHOLE_DESCRIPTOR_ONLY,

  /**
   * Roots that may receive some part of the walked files' content: every production include edge is followed,
   * because a sub-selecting includer may still receive a plugin alias when descriptors are pre-merged at build time.
   */
  ANY_EDGE,
}

/** A `<content>` entry for a module descriptor: the file declaring the entry and the entry's loading rule. */
internal class ContentModuleEntryEdge(val entryFile: XmlFile, val loading: ModuleLoadingRule?)

/**
 * The `<content>` entries for [descriptor] among [entryFiles]. Extracted on demand: only carriers of a checked
 * registration ever need their entries, so the crawl must not pay for DOM extraction on every node.
 */
internal fun contentModuleEntriesFor(descriptor: XmlFile, entryFiles: List<XmlFile>): List<ContentModuleEntryEdge> {
  val moduleName = descriptor.virtualFile?.nameWithoutExtension ?: return emptyList()
  return entryFiles.flatMap { entryFile ->
    DescriptorUtil.getIdeaPlugin(entryFile)?.content.orEmpty()
      .flatMap { it.moduleEntry }
      // slash-normalized on purpose, unlike SplitModeInspectionUtil's exact-name entry filter
      .filter { it.name.stringValue?.replace('/', '.') == moduleName }
      // verified via ModuleDescriptorNameConverter: a same-basename file in an unrelated module must not match
      .filter { it.name.value?.let(DomUtil::getFile) == descriptor }
      .map { ContentModuleEntryEdge(entryFile, it.loading.value) }
  }
}

/**
 * Immutable snapshot of every descriptor file reachable UPWARD from the crawl seeds through any edge kind:
 * production `xi:include` includers, production `<depends config-file="...">` declarers, and `<content>`
 * includers. Which edges count for which question is decided by the queries, never here.
 */
internal class DescriptorUpGraph private constructor(
  private val includeEdges: Map<XmlFile, List<ProductionXIncludeEdge>>,
  private val dependsEdges: Map<XmlFile, List<ConfigFileDependsEdge>>,
  private val contentIncluders: Map<XmlFile, List<XmlFile>>,
  private val independentlyLoaded: Set<XmlFile>,
) {

  fun dependsEdgesOf(file: XmlFile): List<ConfigFileDependsEdge> = dependsEdges[file].orEmpty()

  fun contentIncludersOf(file: XmlFile): List<XmlFile> = contentIncluders[file].orEmpty()

  fun mergeRoots(file: XmlFile, filter: IncludeEdgeFilter): Set<XmlFile> = mergeRoots(listOf(file), filter)

  /**
   * The descriptors whose own load merges the given files' content: every root reached through the `xi:include`
   * edges selected by [filter], plus a walked file itself when the runtime loads it directly or nothing includes
   * it at all. An included file selected by no followed edge yields no root: it is never loaded on its own, so it
   * must not vouch for itself. Rootness tests the unfiltered edge list; [filter] only limits which edges are walked.
   */
  fun mergeRoots(files: Collection<XmlFile>, filter: IncludeEdgeFilter): Set<XmlFile> {
    val roots = HashSet<XmlFile>()
    val visited = HashSet<XmlFile>()
    val queue = ArrayDeque<XmlFile>()
    files.forEach { if (visited.add(it)) queue.add(it) }
    while (queue.isNotEmpty()) {
      val file = queue.removeFirst()
      val edges = includeEdges[file].orEmpty()
      if (edges.isEmpty() || file in independentlyLoaded) roots.add(file)
      edges
        .filter { filter == IncludeEdgeFilter.ANY_EDGE || it.mergesWholeDescriptor }
        .forEach { if (visited.add(it.includer)) queue.add(it.includer) }
    }
    return roots
  }

  companion object {

    /** One BFS with one visited set records every up-edge kind of every reachable node; queries stay pure set algebra. */
    fun crawl(seeds: Collection<XmlFile>): DescriptorUpGraph {
      val includeEdges = HashMap<XmlFile, List<ProductionXIncludeEdge>>()
      val dependsEdges = HashMap<XmlFile, List<ConfigFileDependsEdge>>()
      val contentIncluders = HashMap<XmlFile, List<XmlFile>>()
      val independentlyLoaded = HashSet<XmlFile>()
      val visited = HashSet<XmlFile>()
      val queue = ArrayDeque<XmlFile>()
      seeds.forEach { if (visited.add(it)) queue.add(it) }
      while (queue.isNotEmpty()) {
        val file = queue.removeFirst()
        val includes = findProductionXIncludeEdges(file)
        val depends = findProductionConfigFileDependsEdges(file)
        val includers = filesWithContentEntryFor(file)
        includeEdges[file] = includes
        dependsEdges[file] = depends
        contentIncluders[file] = includers
        if (isIndependentlyLoadedDescriptor(file)) independentlyLoaded.add(file)
        includes.forEach { if (visited.add(it.includer)) queue.add(it.includer) }
        depends.forEach { if (visited.add(it.declaring)) queue.add(it.declaring) }
        includers.forEach { if (visited.add(it)) queue.add(it) }
      }
      return DescriptorUpGraph(includeEdges, dependsEdges, contentIncluders, independentlyLoaded)
    }
  }
}

/** Guards a recursive descriptor walk against include cycles: a file already on [currentPath] answers [onCycle] without re-entry. */
internal inline fun <T> visitOnce(currentPath: MutableSet<XmlFile>, file: XmlFile, onCycle: T, visit: () -> T): T {
  if (!currentPath.add(file)) return onCycle
  try {
    return visit()
  }
  finally {
    currentPath.remove(file)
  }
}
