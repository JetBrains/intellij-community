// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.codeInspection.options.OptionController
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.siyeh.ig.ui.ExternalizableStringSet
import one.util.streamex.StreamEx
import org.jdom.Element
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Action
import org.jetbrains.idea.devkit.dom.Component
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint

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
   * ([effectiveDeclaredDependencyNames]); a test descriptor reaches the class through its test runtime scope.
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
    if (isInProductionRoots(descriptorFile, elementModule.project)) {
      if (!isInProductionRoots(classFile, elementModule.project)) return false
      return isLoadedByMainPluginClassloader(definingModule) && seesMainPluginClassloader(elementModule) ||
             declaresDependencyOnModule(element, definingModule)
    }
    return isInTestRoots(descriptorFile, elementModule.project) &&
           GlobalSearchScope.moduleRuntimeScope(elementModule, true).contains(classFile)
  }

  private fun declaresDependencyOnModule(element: DomElement, module: Module): Boolean {
    return module.name in effectiveDeclaredDependencyNames(DomUtil.getFile(element)).moduleNames
  }

  @Tag("modules-set")
  class PluginModuleSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    var modules = LinkedHashSet<String>()
  }

}

