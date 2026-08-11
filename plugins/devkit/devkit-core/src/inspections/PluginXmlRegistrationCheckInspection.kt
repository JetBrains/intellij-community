// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.codeInspection.options.OptionController
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xml.util.XmlUtil
import com.siyeh.ig.ui.ExternalizableStringSet
import one.util.streamex.StreamEx
import org.jdom.Element
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Action
import org.jetbrains.idea.devkit.dom.Component
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.util.DescriptorUtil

/**
 * Works only in internal mode and for IntelliJ Project.
 */
internal class PluginXmlRegistrationCheckInspection : DevKitPluginXmlInspectionBase() {

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoreClasses: MutableList<String> = ExternalizableStringSet()

  @XCollection
  var pluginsModules: MutableList<PluginModuleSet> = ArrayList()

  var checkAllPossibleClasses: Boolean = false

  private val myPluginModuleSetsByModuleName = SynchronizedClearableLazy {
    val result = HashMap<String, MutableSet<PluginModuleSet>>()
    pluginsModules.forEach { modulesSet ->
      modulesSet.modules.forEach { module ->
        result.getOrPut(module) { HashSet() }.add(modulesSet)
      }
    }
    result
  }

  override fun getOptionController(): OptionController {
    return super.getOptionController()
      .onValue("pluginsModules",
               { StreamEx.of(pluginsModules).map { set: PluginModuleSet -> java.lang.String.join(",", set.modules) }.toMutableList() },
               { newList: List<String>? ->
                 pluginsModules.clear()
                 StreamEx.of(newList).map { line: String? ->
                   val set = PluginModuleSet()
                   set.modules = StreamEx.split(line, ",").toCollection { LinkedHashSet() }
                   set
                 }.into(pluginsModules)
                 myPluginModuleSetsByModuleName.drop()
               })
  }

  override fun getOptionsPane(): OptPane {
    return pane(
      stringList("ignoreClasses", DevKitBundle.message("inspections.plugin.xml.ignore.classes.title"),
                 JavaClassValidator().withTitle(DevKitBundle.message("inspections.plugin.xml.add.ignored.class.title"))),
      stringList("pluginsModules", DevKitBundle.message("inspections.plugin.xml.plugin.modules.label"))
        .description(DevKitBundle.message("inspections.plugin.xml.plugin.modules.description")),
      checkbox("checkAllPossibleClasses", DevKitBundle.message("inspections.plugin.xml.check.all.possible"))
    )
  }

  override fun readSettings(node: Element) {
    super.readSettings(node)
    myPluginModuleSetsByModuleName.drop()
  }

  fun areModulesInSamePluginSet(moduleName: String, otherModuleName: String): Boolean {
    val setsByModuleName = myPluginModuleSetsByModuleName.value
    val moduleSets = setsByModuleName[moduleName] ?: return false
    val otherModuleSets = setsByModuleName[otherModuleName] ?: return false
    return moduleSets.any(otherModuleSets::contains)
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension &&
        element !is ExtensionPoint &&
        element !is Action &&
        element !is Component) return

    if (!isAllowed(holder)) return

    val registrationChecker =
      ComponentModuleRegistrationChecker(::isResolvableSamePluginRegistration, ignoreClasses, holder)
    if (!registrationChecker.isIdeaPlatformModule(element.module)) return

    if (checkAllPossibleClasses) {
      registrationChecker.checkProperXmlFileForClassesIncludingDependency(element)
    }

    when (element) {
      is Extension -> {
        registrationChecker.checkProperXmlFileForExtension(element)
      }
      is ExtensionPoint -> {
        registrationChecker.checkProperModule(element)
      }
      is Action -> {
        registrationChecker.checkProperXmlFileForClass(element, element.getClazz().getValue())
      }
      is Component -> {
        registrationChecker.checkProperXmlFileForClass(element, element.implementationClass.value)
      }
    }
  }

  /**
   * Modules of one plugin may register each other's classes only when the registration still resolves at runtime:
   * a production registration reaches the class through the plugin's main classloader (the defining module is
   * loaded by it, and the registering module is loaded by it or resolves through it as an optional content module),
   * or through a `<dependencies><module>` entry visible in every runtime context of the registering descriptor
   * ([effectiveDeclaredDependencyModuleNames]); a test descriptor reaches the class through its test runtime scope.
   */
  private fun isResolvableSamePluginRegistration(
    element: DomElement,
    psiClass: PsiClass,
    definingModule: Module,
    elementModule: Module,
  ): Boolean {
    if (!areModulesInSamePluginSet(definingModule.name, elementModule.name)) return false
    val classFile = PsiUtilCore.getVirtualFile(psiClass) ?: return true
    val descriptorFile = DomUtil.getFile(element).virtualFile
    if (isInProductionRoots(descriptorFile, elementModule)) {
      if (!isInProductionRoots(classFile, elementModule)) return false
      return isLoadedByMainPluginClassloader(definingModule) && seesMainPluginClassloader(elementModule) ||
             declaresDependencyOnModule(element, definingModule)
    }
    return isInTestRoots(descriptorFile, elementModule) &&
           GlobalSearchScope.moduleRuntimeScope(elementModule, true).contains(classFile)
  }

  private fun declaresDependencyOnModule(element: DomElement, module: Module): Boolean {
    return module.name in effectiveDeclaredDependencyModuleNames(DomUtil.getFile(element))
  }

  private fun isInProductionRoots(file: VirtualFile?, module: Module): Boolean {
    val fileIndex = ProjectFileIndex.getInstance(module.project)
    return file != null && fileIndex.isInSourceContent(file) && !fileIndex.isInTestSourceContent(file)
  }

  private fun isInTestRoots(file: VirtualFile?, module: Module): Boolean {
    return file != null && ProjectFileIndex.getInstance(module.project).isInTestSourceContent(file)
  }

  @Tag("modules-set")
  class PluginModuleSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    var modules = LinkedHashSet<String>()
  }

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

/**
 * Module names declared in `<dependencies>` that cover registrations in this file at runtime. The file may be merged
 * into several descriptors via `xi:include` and the registration must resolve in each of them, so a name counts only
 * when every production include context sees it; test-source includers describe no production classloader and are
 * ignored. A file loaded on its own — a plugin's `META-INF/plugin.xml` or a content module descriptor — is itself a
 * runtime context that sees only its own declarations, so include contexts cannot add names for it. An includer
 * merging only a selected part of the file (a sub-selecting `xpointer`) leaves the file's own `<dependencies>`
 * behind, so only the includer's names count in that context.
 */
private fun effectiveDeclaredDependencyModuleNames(file: XmlFile): Set<String> {
  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(
      collectDependencyModuleNamesVisibleInEveryContext(file, HashSet()),
      ProjectRootModificationTracker.getInstance(file.project),
      PsiManager.getInstance(file.project).modificationTracker.forLanguage(XMLLanguage.INSTANCE),
    )
  }
}

private fun collectDependencyModuleNamesVisibleInEveryContext(file: XmlFile, currentPath: MutableSet<XmlFile>): Set<String> {
  if (!currentPath.add(file)) return emptySet()
  try {
    val ownNames = HashSet<String>()
    DescriptorUtil.getIdeaPlugin(file)?.dependencies?.moduleEntry?.forEach { entry ->
      entry.name.stringValue?.let(ownNames::add)
    }
    val contexts = ArrayList<Set<String>>()
    if (isIndependentlyLoadedDescriptor(file)) {
      contexts.add(ownNames)
    }
    findProductionXIncludeEdges(file).mapTo(contexts) { edge ->
      val includerNames = collectDependencyModuleNamesVisibleInEveryContext(edge.includer, currentPath)
      if (edge.mergesWholeDescriptor) ownNames + includerNames else includerNames
    }
    return contexts.reduceOrNull { common, next -> common.intersect(next) } ?: ownNames
  }
  finally {
    currentPath.remove(file)
  }
}

/**
 * A descriptor the runtime loads on its own: a plugin's `META-INF/plugin.xml` or a module descriptor referenced from
 * a production `<content>` entry. Descriptors xi-including such a file only reuse its content and do not relax what
 * its own load resolves.
 */
private fun isIndependentlyLoadedDescriptor(file: XmlFile): Boolean {
  return file.name == "plugin.xml" && file.parent?.name == "META-INF" || isLoadedAsContentModule(file)
}

private fun isLoadedAsContentModule(file: XmlFile): Boolean {
  val virtualFile = file.virtualFile ?: return false
  return PluginIdDependenciesIndex.findFilesIncludingContentModule(file.project, virtualFile).isNotEmpty()
}

internal data class ProductionXIncludeEdge(val includer: XmlFile, val mergesWholeDescriptor: Boolean)

internal fun findProductionXIncludeEdges(file: XmlFile): List<ProductionXIncludeEdge> {
  val fileIndex = ProjectFileIndex.getInstance(file.project)
  return ReferencesSearch.search(file).findAll().mapNotNull { reference ->
    val tag = reference.element.parentOfType<XmlTag>() ?: return@mapNotNull null
    if (tag.namespace != XmlUtil.XINCLUDE_URI || tag.localName != "include") return@mapNotNull null
    val includer = reference.element.containingFile as? XmlFile ?: return@mapNotNull null
    val virtualFile = includer.virtualFile ?: return@mapNotNull null
    if (!fileIndex.isInSourceContent(virtualFile) || fileIndex.isInTestSourceContent(virtualFile)) return@mapNotNull null
    ProductionXIncludeEdge(includer, mergesWholeDescriptor(tag))
  }.distinct()
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
