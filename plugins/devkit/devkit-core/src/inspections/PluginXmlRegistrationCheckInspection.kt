// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.codeInspection.options.OptionController
import com.intellij.openapi.module.Module
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.DomElement
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

  private val myPluginModuleSetByModuleName = SynchronizedClearableLazy {
    val result: MutableMap<String, PluginModuleSet> = HashMap()
    for (modulesSet in pluginsModules) {
      for (module in modulesSet.modules) {
        result[module] = modulesSet
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
                 myPluginModuleSetByModuleName.drop()
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
    myPluginModuleSetByModuleName.drop();
  }

  fun findPluginModuleSet(moduleName: String): PluginModuleSet? = myPluginModuleSetByModuleName.value[moduleName]

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension &&
        element !is ExtensionPoint &&
        element !is Action &&
        element !is Component) return;

    if (!isAllowed(holder)) return

    val registrationChecker =
      ComponentModuleRegistrationChecker(myPluginModuleSetByModuleName, ignoreClasses, holder)
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

  @Tag("modules-set")
  class PluginModuleSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    var modules = LinkedHashSet<String>();
  }

}

/**
 * Modules listed in one `pluginsModules` set are assembled into a single plugin and share its registration scope, so
 * registered names (extension points, actions, groups) resolve between them without a JPS dependency edge. They do not
 * necessarily share a classloader: a non-embedded content module loads classes and bundles with its own classloader.
 * [module] is not a sibling of itself: a reference inside one module is decided by that module's runtime scope, which
 * tells production and test roots apart.
 * Returns `false` when [PluginXmlRegistrationCheckInspection] is absent from the profile or declares no set containing [module].
 */
internal fun areSiblingModulesInSamePlugin(module: Module, otherModule: Module, context: PsiElement): Boolean {
  if (module == otherModule) return false
  val inspection = InspectionProjectProfileManager.getInstance(module.project).currentProfile
                     .getUnwrappedTool(REGISTRATION_CHECK_SHORT_NAME, context) as? PluginXmlRegistrationCheckInspection
                   ?: return false
  val moduleSet = inspection.findPluginModuleSet(module.name) ?: return false
  return moduleSet === inspection.findPluginModuleSet(otherModule.name)
}

private const val REGISTRATION_CHECK_SHORT_NAME = "PluginXmlRegistrationCheck"
