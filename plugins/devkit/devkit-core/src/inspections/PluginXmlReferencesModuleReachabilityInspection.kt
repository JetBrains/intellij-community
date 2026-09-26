// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.properties.BundleNameEvaluator
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.ResourceBundleReference
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlFile
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.ActionOrGroup
import org.jetbrains.idea.devkit.dom.AddToGroup
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex
import org.jetbrains.idea.devkit.dom.processing.isClassRegistration
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.references.ActionOrGroupIdReference
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class PluginXmlReferencesModuleReachabilityInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (!isAllowed(holder)) return

    when (element) {
      is Extension -> {
        val module = element.module ?: return
        val extensionPoint = element.extensionPoint ?: return
        val epTag = extensionPoint.xmlTag
        val epFqn = extensionPoint.effectiveQualifiedName
        if (!isEpReachableFromModuleByFqn(epFqn, element, module) &&
            !isInSiblingModuleOfSamePlugin(element, epTag, module) &&
            !isReachableViaDeclaredPluginDependency(element) { extensionPointDeclarationFiles(it, epFqn) }) {
          holder.reportUnreachableClassProblem(
            element, epTag, epFqn, module,
            "inspection.plugin.xml.references.module.reachability.extension.point"
          )
        }
      }

      is GenericDomValue<*> -> {
        val module = element.module ?: return
        if (hasPsiClassValueType(element)) {
          if (isClassRegistration(element)) return // handled by ComponentModuleRegistrationChecker
          val value = element.value as? PsiClass
          if (value != null) {
            val qualifiedName = value.qualifiedName
            if (!isClassReachableFromModuleByFqn(qualifiedName, element, module) &&
                !isInSiblingModuleReachableViaMainPluginClassloader(element, value, module)) {
              val referencedClassName = qualifiedName ?: value.name ?: ""
              holder.reportUnreachableClassProblem(
                element, value, referencedClassName, module,
                "inspection.plugin.xml.references.module.reachability.class"
              )
            }
          }
        }

        val referencesElement = (if (element is GenericAttributeValue<*>) element.xmlAttributeValue else element.xmlElement)
          ?: return
        for (ref in referencesElement.references) {
          when (ref) {
            is ActionOrGroupIdReference -> {
              if (isPositioningOnlyReference(element)) continue
              val target = ref.resolve() ?: continue
              if (!isReachableFromModule(element, target, module) &&
                  !isActionOrGroupReachableFromModuleById(ref.canonicalText, element, module) &&
                  !isInSiblingModuleOfSamePlugin(element, target, module) &&
                  !isReachableViaDeclaredPluginDependency(element) { actionOrGroupRegistrationFiles(it, ref.canonicalText) }) {
                holder.reportUnreachableClassProblem(
                  element, target, ref.canonicalText, module,
                  "inspection.plugin.xml.references.module.reachability.action.or.group"
                )
              }
            }

            is ResourceBundleReference -> {
              val target = ref.resolve() ?: continue
              if (!isReachableFromModule(element, target, module) &&
                  !isBundleReachableFromModule(ref.canonicalText, element, module) &&
                  !isInSiblingModuleReachableViaMainPluginClassloader(element, target, module)) {
                holder.reportUnreachableClassProblem(
                  element, target, ref.canonicalText, module,
                  "inspection.plugin.xml.references.module.reachability.bundle"
                )
              }
            }
          }
        }
      }
    }
  }

  /**
   * A positioning-only reference degrades gracefully at runtime, so it needs no reachability: an unresolvable
   * `relative-to-action` anchor silently falls back to the end of the group, and a missing `use-shortcut-of`
   * base action only leaves the action without a shortcut.
   */
  private fun isPositioningOnlyReference(element: GenericDomValue<*>): Boolean {
    return when (val parent = element.parent) {
      is AddToGroup -> element == parent.relativeToAction
      is ActionOrGroup -> element == parent.useShortcutOf
      else -> false
    }
  }

  /**
   * Checked before forcing [GenericDomValue.getValue]: conversion of a non-class value may be arbitrarily
   * expensive (a `language` attribute enumerates every `Language` inheritor in the project), and only class
   * values matter here.
   */
  private fun hasPsiClassValueType(element: GenericDomValue<*>): Boolean {
    val parameter = DomUtil.getGenericValueParameter(element.domElementType) ?: return false
    return PsiClass::class.java.isAssignableFrom(parameter)
  }

  private fun isEpReachableFromModuleByFqn(fqn: String, element: DomElement, module: Module): Boolean {
    return ExtensionPointIndex.findExtensionPoint(module.project, moduleRuntimeScope(element, module), fqn) != null
  }

  private fun isClassReachableFromModuleByFqn(fqn: String?, element: DomElement, module: Module): Boolean {
    return fqn == null || JavaPsiFacade.getInstance(module.project).findClass(fqn, moduleRuntimeScope(element, module)) != null
  }

  private fun isReachableFromModule(element: DomElement, target: PsiElement, module: Module): Boolean {
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return true
    return moduleRuntimeScope(element, module).contains(targetFile)
  }

  /**
   * Checked last, for names resolved through the plugin's registration scope (extension points, actions, groups):
   * modules of one plugin register into a shared scope, so such names resolve between them without a dependency edge.
   * A plugin is built from production roots alone, so the exemption holds only between production roots: a test
   * descriptor lives on the test classpath, where reachability is decided by dependency edges; unmarked content
   * directories are built into nothing.
   * Classes and resource bundles resolve through classloaders, not the registration scope, and qualify only via
   * [isInSiblingModuleReachableViaMainPluginClassloader].
   */
  private fun isInSiblingModuleOfSamePlugin(element: DomElement, target: PsiElement?, module: Module): Boolean {
    val targetModule = target?.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return false
    val descriptorFile = DomUtil.getFile(element)
    return isInProductionRoots(PsiUtilCore.getVirtualFile(target), module.project) &&
           isInProductionRoots(descriptorFile.virtualFile, module.project) &&
           areSiblingModulesInSamePlugin(module, targetModule, descriptorFile)
  }

  /**
   * Checked last, for classes and resource bundles: they resolve through classloaders, and without a dependency edge
   * a sibling's class resolves only through the plugin's main classloader. The target must be loaded by it
   * ([isLoadedByMainPluginClassloader]); the referencing module must be loaded by it too, or resolve its classes as
   * an optional content module through the implicit runtime dependency on main ([seesMainPluginClassloader]).
   */
  private fun isInSiblingModuleReachableViaMainPluginClassloader(element: DomElement, target: PsiElement?, module: Module): Boolean {
    val targetModule = target?.let { ModuleUtilCore.findModuleForPsiElement(it) } ?: return false
    return isInSiblingModuleOfSamePlugin(element, target, module) &&
           seesMainPluginClassloader(module) &&
           isLoadedByMainPluginClassloader(targetModule)
  }

  /**
   * Checked last, for names resolved through global registries (extension points, actions, groups): a descriptor
   * declaring `<dependencies><plugin id="..."/>` loads only when that plugin is present, so whatever the plugin's
   * own merged descriptor registers exists by construction — no JPS edge models this guarantee, and none is needed,
   * since registries resolve ids without classloaders. The declared id may be a plugin alias declared by several
   * descriptors; each of them must then carry a registration of the referenced name — its own declaration counts,
   * not only the one the reference resolved to — or a product satisfying the gate without it would leave the
   * reference dangling. The two walks approximate in opposite directions: a registration counts only for roots
   * merging the whole carrier descriptor, while the alias counts for every root reached through any production
   * include edge — build-time descriptor pre-merge may hand a sub-selecting includer the alias without the
   * registration. A registration in a content module also counts for the root including it when the module loads
   * unconditionally with that root; a provider that is itself a content-module descriptor defers to every plugin
   * including it (see [loadGuaranteedRoots]). A `<dependencies><module>` gate is honored the same way,
   * with the named module's descriptor file as the sole provider. Gates close transitively over provider
   * requirements: a name required by every provider of a satisfied gate is enabled with it, so registrations of a
   * plugin no gate provider can load without count too (see [closeOverProviderRequirements]). A content-module
   * descriptor — a gate provider or the referencing descriptor itself — additionally enables whatever every plugin
   * packaging it requires, since the module loads only inside one of them (see [namesEnabledWhenLoaded]).
   * A registration in a `<depends config-file="...">`
   * sub-descriptor counts when the referencing descriptor also gates on the depends target
   * (see [gatedCarrierRoots]). The walk sees project sources only: a library-provided descriptor
   * qualifies neither as a provider nor as a carrier.
   */
  private fun isReachableViaDeclaredPluginDependency(element: DomElement, registrationFiles: (Project) -> Collection<VirtualFile>): Boolean {
    val descriptorFile = DomUtil.getFile(element)
    val project = descriptorFile.project
    if (!isInProductionRoots(descriptorFile.virtualFile, project)) return false
    val declaredGates = namesEnabledWhenLoaded(descriptorFile)
    if (declaredGates.pluginIds.isEmpty() && declaredGates.moduleNames.isEmpty()) return false
    val gates = closeOverProviderRequirements(declaredGates, project)
    val registrationDescriptors = productionDescriptorFiles(registrationFiles(project), project)
    val carriers = gatedCarrierRoots(DescriptorUpGraph.crawl(registrationDescriptors), registrationDescriptors, gates.pluginIds)
    if (carriers.isEmpty()) return false
    return gates.pluginIds.any { it.isNotBlank() && everyPluginGateProviderIsLoadGuaranteed(it, carriers, project) } ||
           gates.moduleNames.any { it.isNotBlank() && everyModuleGateProviderIsLoadGuaranteed(it, carriers, project) }
  }

  private fun productionDescriptorFiles(files: Collection<VirtualFile>, project: Project): List<XmlFile> {
    val psiManager = PsiManager.getInstance(project)
    return files
      .filter { isInProductionRoots(it, project) }
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
  }

  /**
   * The roots whose own load merges the whole descriptor of a registration file, expanded through gated `<depends>`
   * edges: a carrier loaded as a `<depends config-file="...">` sub-descriptor — optional or not — is present whenever
   * the declaring descriptor is loaded and the depends target plugin is enabled. When the referencing descriptor
   * itself gates on that target ([gatePluginIds]), every root merging the declaring descriptor guarantees the carrier,
   * so those roots carry the registration too.
   */
  private fun gatedCarrierRoots(graph: DescriptorUpGraph, registrationFiles: Collection<XmlFile>, gatePluginIds: Set<String>): Set<XmlFile> {
    val carrierRoots = graph.mergeRoots(registrationFiles, IncludeEdgeFilter.WHOLE_DESCRIPTOR_ONLY)
    if (gatePluginIds.isEmpty()) return carrierRoots
    val expanded = HashSet(carrierRoots)
    val queue = ArrayDeque(carrierRoots)
    while (queue.isNotEmpty()) {
      graph.dependsEdgesOf(queue.removeFirst())
        .filter { it.dependsPluginId != null && it.dependsPluginId in gatePluginIds }
        .flatMap { graph.mergeRoots(it.declaring, IncludeEdgeFilter.WHOLE_DESCRIPTOR_ONLY) }
        .forEach { if (expanded.add(it)) queue.add(it) }
    }
    return expanded
  }

  /**
   * The providers of a `<dependencies><plugin id="..."/>` gate: every production descriptor declaring the id as its
   * plugin id or an alias, expanded through every production include edge — build-time descriptor pre-merge may hand
   * a sub-selecting includer the alias without the registration. Every provider must be load-guaranteed, or a product
   * satisfying the gate through the others would leave the reference dangling.
   */
  private fun everyPluginGateProviderIsLoadGuaranteed(gate: String, carriers: Set<XmlFile>, project: Project): Boolean {
    val declaringFiles = productionDescriptorFiles(PluginIdModuleIndex.getFiles(project, gate), project)
    if (declaringFiles.isEmpty()) return false
    val graph = DescriptorUpGraph.crawl(declaringFiles + carriers)
    val providers = graph.mergeRoots(declaringFiles, IncludeEdgeFilter.ANY_EDGE)
    return providers.isNotEmpty() && loadGuaranteedRoots(graph, carriers, providers).containsAll(providers)
  }

  /**
   * The providers of a `<dependencies><module name="..."/>` gate: the declaring descriptor loads only when the named
   * content module is loaded, so whatever that module's descriptor registers — or every plugin packaging it
   * guarantees — exists by construction. The providers are the descriptor files themselves, without merge-root
   * expansion.
   */
  private fun everyModuleGateProviderIsLoadGuaranteed(gate: String, carriers: Set<XmlFile>, project: Project): Boolean {
    val providers = moduleGateProviders(project, gate)
    if (providers.isEmpty()) return false
    val graph = DescriptorUpGraph.crawl(providers + carriers)
    return loadGuaranteedRoots(graph, carriers, providers.toSet()).containsAll(providers)
  }

  /**
   * The gate name maps to the descriptor file the way the runtime locates it: dots for slashes, plus `.xml`, inside
   * the module the name denotes — `foo.bar` in module `foo.bar`, `foo/bar` in module `foo`. A same-named file in any
   * other module belongs to a different module name and never loads as the gate module.
   */
  private fun moduleGateProviders(project: Project, gate: String): List<XmlFile> {
    val psiManager = PsiManager.getInstance(project)
    return FilenameIndex.getVirtualFilesByName(gate.replace('/', '.') + ".xml", GlobalSearchScope.projectScope(project))
      .filter { isInProductionRoots(it, project) }
      .mapNotNull { psiManager.findFile(it) as? XmlFile }
      .filter { DescriptorUtil.getIdeaPlugin(it) != null && isDescriptorOfDenotedJpsModule(it, gate) }
  }

  /**
   * Names enabled whenever the declared gates are satisfied, closed transitively: an enabled name means some
   * provider of it is loaded, so a name required by every provider of an enabled name is enabled too — a reference
   * gated on plugin A resolves through registrations of plugin B when no provider of A can load without B.
   * Intersection over providers keeps the closure sound when an id or alias has several declarers; a name with no
   * project-source providers is not expanded. A provider loaded only as a content module also requires whatever
   * every plugin packaging it requires, since it loads only inside one of them (see [namesEnabledWhenLoaded]).
   */
  private fun closeOverProviderRequirements(gates: DeclaredDependencyNames, project: Project): DeclaredDependencyNames {
    val pluginIds = LinkedHashSet(gates.pluginIds)
    val moduleNames = LinkedHashSet(gates.moduleNames)
    val pluginQueue = ArrayDeque(pluginIds.filter { it.isNotBlank() })
    val moduleQueue = ArrayDeque(moduleNames.filter { it.isNotBlank() })
    while (pluginQueue.isNotEmpty() || moduleQueue.isNotEmpty()) {
      val providers =
        if (pluginQueue.isNotEmpty()) productionDescriptorFiles(PluginIdModuleIndex.getFiles(project, pluginQueue.removeFirst()), project)
        else moduleGateProviders(project, moduleQueue.removeFirst())
      val required = providers.map { namesEnabledWhenLoaded(it) }.reduceOrNull(DeclaredDependencyNames::intersect) ?: continue
      required.pluginIds.forEach { if (it.isNotBlank() && pluginIds.add(it)) pluginQueue.add(it) }
      required.moduleNames.forEach { if (it.isNotBlank() && moduleNames.add(it)) moduleQueue.add(it) }
    }
    return DeclaredDependencyNames(moduleNames, pluginIds)
  }

  /**
   * The descriptors whose load guarantees the referenced registration, decided for [providers] and every descriptor
   * their guarantee depends on. A descriptor qualifies when the registration is in its own merged descriptor
   * ([carriers]), or when a carrier is a content module loading unconditionally with it — the `<content>` entries
   * for the carrier in its merged descriptor are nonempty and all say `loading="required"` or `embedded`; optional
   * and on-demand modules may stay unloaded while the gate is satisfied, so a descriptor without entries guarantees
   * nothing. A descriptor that is itself a content-module descriptor loads only inside some plugin that packaged it,
   * so it qualifies once every production descriptor including it qualifies, whichever one the running product
   * chose — iterated to the least fixpoint, so descriptors vouching only for each other never qualify.
   */
  private fun loadGuaranteedRoots(graph: DescriptorUpGraph, carriers: Set<XmlFile>, providers: Set<XmlFile>): Set<XmlFile> {
    val includingPluginRoots = HashMap<XmlFile, Set<XmlFile>>()
    val queue = ArrayDeque(providers + carriers)
    while (queue.isNotEmpty()) {
      val file = queue.removeFirst()
      if (file in includingPluginRoots) continue
      val roots = graph.contentIncludersOf(file).flatMapTo(HashSet()) { graph.mergeRoots(it, IncludeEdgeFilter.ANY_EDGE) }
      includingPluginRoots[file] = roots
      queue.addAll(roots)
    }
    val entriesByCarrier = HashMap<XmlFile, List<ContentModuleEntryEdge>>()
    fun loadsUnconditionallyWith(provider: XmlFile, carrier: XmlFile): Boolean {
      val entries = entriesByCarrier
        .getOrPut(carrier) { contentModuleEntriesFor(carrier, graph.contentIncludersOf(carrier)) }
        .filter { provider in graph.mergeRoots(it.entryFile, IncludeEdgeFilter.WHOLE_DESCRIPTOR_ONLY) }
      return entries.isNotEmpty() && entries.all { it.loading == ModuleLoadingRule.REQUIRED || it.loading == ModuleLoadingRule.EMBEDDED }
    }
    val guaranteed = includingPluginRoots.keys.filterTo(HashSet()) { file ->
      file in carriers || carriers.any { loadsUnconditionallyWith(file, it) }
    }
    var changed = true
    while (changed) {
      changed = false
      includingPluginRoots.forEach { (file, roots) ->
        if (file !in guaranteed && roots.isNotEmpty() && roots.all(guaranteed::contains)) {
          guaranteed.add(file)
          changed = true
        }
      }
    }
    return guaranteed
  }

  private fun extensionPointDeclarationFiles(project: Project, fqn: String): Collection<VirtualFile> {
    return ExtensionPointIndex.getFiles(GlobalSearchScope.projectScope(project), fqn)
  }

  private fun actionOrGroupRegistrationFiles(project: Project, id: String): Collection<VirtualFile> {
    val files = HashSet<VirtualFile>()
    IdeaPluginRegistrationIndex.processActionOrGroup(project, id, GlobalSearchScope.projectScope(project), Processor { registration: ActionOrGroup ->
      files.add(DomUtil.getFile(registration).virtualFile)
      true
    })
    return files
  }

  private fun isActionOrGroupReachableFromModuleById(id: String, element: DomElement, module: Module): Boolean {
    return !IdeaPluginRegistrationIndex.processActionOrGroup(module.project, id, moduleRuntimeScope(element, module)) { false }
  }

  private fun isBundleReachableFromModule(bundleName: String, element: DomElement, module: Module): Boolean {
    return PropertiesReferenceManager.getInstance(module.project)
      .findPropertiesFiles(moduleRuntimeScope(element, module), bundleName, BundleNameEvaluator.DEFAULT)
      .isNotEmpty()
  }

  private fun moduleRuntimeScope(element: DomElement, module: Module): GlobalSearchScope {
    val includeTests = isInTestRoots(DomUtil.getFile(element).virtualFile, module.project)
    return GlobalSearchScope.moduleRuntimeScope(module, includeTests)
  }

  private fun DomElementAnnotationHolder.reportUnreachableClassProblem(
    element: DomElement,
    referencedElement: PsiElement,
    referencedClassName: String,
    module: Module,
    @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) messageKey: String
  ) {
    val targetModuleName = resolveLocationName(referencedElement)
    this.createProblem(
      element,
      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
      message(messageKey, referencedClassName, module.name, targetModuleName),
      null,
      *createFixes(element, referencedElement, module)
    )
  }

  private fun resolveLocationName(target: PsiElement): String {
    ModuleUtilCore.findModuleForPsiElement(target)?.let { return " (module '${it.name}')" }
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return ""
    ProjectFileIndex.getInstance(target.project).findContainingLibraries(targetFile).firstOrNull()
      ?.let { return " (library '${it.name}')" }
    return ""
  }

  /**
   * A test descriptor lives on the test classpath only, which a test dependency already reaches whatever root the target
   * is in; a production descriptor is packaged from production output alone, so only a production target closes its gap.
   * No fix when no scope reaches the target: a module cannot depend on itself, and unmarked content directories are
   * built into nothing.
   */
  private fun createFixes(element: DomElement, target: PsiElement, module: Module): Array<LocalQuickFix> {
    val targetModule = ModuleUtilCore.findModuleForPsiElement(target) ?: return emptyArray()
    if (targetModule == module) return emptyArray()
    val targetFile = PsiUtilCore.getVirtualFile(target)
    val targetInProductionRoots = isInProductionRoots(targetFile, module.project)
    val scope = when {
      isInTestRoots(DomUtil.getFile(element).virtualFile, module.project) &&
      (targetInProductionRoots || isInTestRoots(targetFile, module.project)) -> DependencyScope.TEST
      targetInProductionRoots -> DependencyScope.COMPILE
      else -> return emptyArray()
    }
    // no plugin descriptor covers test output, so a descriptor dependency would describe nothing
    val descriptorDependency = if (targetInProductionRoots) resolveDescriptorDependency(targetModule) else null
    return arrayOf(
      AddModuleDependencyFix(
        targetModuleName = targetModule.name,
        dependencyScope = scope,
        descriptorDependencyId = descriptorDependency?.first,
        isContentModuleDependency = descriptorDependency?.second ?: false,
      )
    )
  }

  private fun resolveDescriptorDependency(targetModule: Module): Pair<String, Boolean>? {
    val contentDescriptor = PluginModuleType.getContentModuleDescriptorXml(targetModule)
    if (contentDescriptor != null) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(contentDescriptor)
      if (ideaPlugin != null) {
        val moduleName = contentDescriptor.name.removeSuffix(".xml")
        return Pair(moduleName, true)
      }
    }

    val pluginXml = PluginModuleType.getPluginXml(targetModule)
    if (pluginXml != null) {
      val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml)
      if (ideaPlugin != null && ideaPlugin.hasRealPluginId()) {
        return Pair(ideaPlugin.pluginId!!, false)
      }
    }

    return null
  }
}

private class AddModuleDependencyFix(
  private val targetModuleName: String,
  private val dependencyScope: DependencyScope,
  private val descriptorDependencyId: String?,
  private val isContentModuleDependency: Boolean,
) : LocalQuickFix {

  private val isTestDependency: Boolean get() = dependencyScope == DependencyScope.TEST

  override fun getFamilyName(): String {
    return message("inspection.plugin.xml.references.module.reachability.fix.family.name")
  }

  override fun getName(): String {
    return if (isTestDependency) {
      message("inspection.plugin.xml.references.module.reachability.fix.name.test", targetModuleName)
    } else {
      message("inspection.plugin.xml.references.module.reachability.fix.name", targetModuleName)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val text = when {
      descriptorDependencyId != null && isTestDependency ->
        message("inspection.plugin.xml.references.module.reachability.fix.preview.test.with.descriptor", targetModuleName, descriptorDependencyId)
      descriptorDependencyId != null ->
        message("inspection.plugin.xml.references.module.reachability.fix.preview.with.descriptor", targetModuleName, descriptorDependencyId)
      isTestDependency -> message("inspection.plugin.xml.references.module.reachability.fix.preview.test", targetModuleName)
      else -> message("inspection.plugin.xml.references.module.reachability.fix.preview", targetModuleName)
    }
    return IntentionPreviewInfo.Html(HtmlChunk.text(text))
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return
    val targetModule = ModuleManager.getInstance(project).findModuleByName(targetModuleName) ?: return
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(module, targetModule, dependencyScope, false)
      .onSuccess {
        ReadAction.nonBlocking<IdeaPlugin?> {
          resolveIdeaPlugin(descriptor)
        }.finishOnUiThread(ModalityState.nonModal()) { ideaPlugin ->
          if (ideaPlugin != null) {
            WriteCommandAction.runWriteCommandAction(project, familyName, null, {
              addDescriptorDependency(ideaPlugin)
            })
          }
        }.submit(AppExecutorUtil.getAppExecutorService())
      }
  }

  private fun resolveIdeaPlugin(descriptor: ProblemDescriptor): IdeaPlugin? {
    if (descriptorDependencyId == null) return null
    val domElement = DomUtil.getDomElement(descriptor.psiElement) ?: return null
    return domElement.getParentOfType(IdeaPlugin::class.java, true)
  }

  private fun addDescriptorDependency(ideaPlugin: IdeaPlugin) {
    if (isContentModuleDependency) {
      val dependencies = ideaPlugin.dependencies
      if (dependencies.moduleEntry.none { it.name.stringValue == descriptorDependencyId }) {
        dependencies.addModuleEntry().name.stringValue = descriptorDependencyId
      }
    } else if (ideaPlugin.isV2Descriptor) {
      val dependencies = ideaPlugin.dependencies
      if (dependencies.plugin.none { it.id.stringValue == descriptorDependencyId }) {
        dependencies.addPlugin().id.stringValue = descriptorDependencyId
      }
    } else {
      if (ideaPlugin.depends.none { (it.rawText ?: it.stringValue) == descriptorDependencyId }) {
        ideaPlugin.addDependency().stringValue = descriptorDependencyId
      }
    }
  }
}
