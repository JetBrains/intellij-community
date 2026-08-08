// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.properties.BundleNameEvaluator
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.ResourceBundleReference
import com.intellij.lang.xml.XMLLanguage
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
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import com.intellij.xml.util.XmlUtil
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.dom.ContentDescriptor
import org.jetbrains.idea.devkit.dom.ContentDescriptor.ModuleDescriptor.ModuleLoadingRule
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.dom.processing.isClassRegistration
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil
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
            !isInSiblingModuleOfSamePlugin(element, epTag, module)) {
          holder.reportUnreachableClassProblem(
            element, epTag, epFqn, module,
            "inspection.plugin.xml.references.module.reachability.extension.point"
          )
        }
      }

      is GenericDomValue<*> -> {
        val module = element.module ?: return
        val value = element.value
        if (value is PsiClass) {
          if (isClassRegistration(element)) return // handled by ComponentModuleRegistrationChecker
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

        val referencesElement = (if (element is GenericAttributeValue<*>) element.xmlAttributeValue else element.xmlElement)
          ?: return
        for (ref in referencesElement.references) {
          when (ref) {
            is ActionOrGroupIdReference -> {
              val target = ref.resolve() ?: continue
              if (!isReachableFromModule(element, target, module) &&
                  !isActionOrGroupReachableFromModuleById(ref.canonicalText, element, module) &&
                  !isInSiblingModuleOfSamePlugin(element, target, module)) {
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

  private fun isInTestRoots(file: VirtualFile?, project: Project): Boolean {
    return file != null && ProjectFileIndex.getInstance(project).isInTestSourceContent(file)
  }

  private fun isInProductionRoots(file: VirtualFile?, project: Project): Boolean {
    val fileIndex = ProjectFileIndex.getInstance(project)
    return file != null && fileIndex.isInSourceContent(file) && !fileIndex.isInTestSourceContent(file)
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
      ProjectRootModificationTracker.getInstance(project),
      PsiManager.getInstance(project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
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
  if (!currentPath.add(file)) return false
  try {
    DescriptorUtil.getIdeaPlugin(file)?.pluginId?.let { return it == PluginManagerCore.CORE_PLUGIN_ID }
    val includers = findProductionXIncludeEdges(file).filter { it.mergesWholeDescriptor }.map { it.includer }
    if (includers.isNotEmpty()) return includers.all { isPartOfCorePluginDescriptor(it, currentPath) }
    return resolveEffectivePluginId(file, HashSet()) == PluginManagerCore.CORE_PLUGIN_ID
  }
  finally {
    currentPath.remove(file)
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

private fun resolveXIncludeTargetFile(includeTag: XmlTag): XmlFile? {
  val href = includeTag.getAttribute("href")?.valueElement ?: return null
  return href.references.maxByOrNull { it.rangeInElement.startOffset }?.resolve() as? XmlFile
}
