// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.application.options

import com.intellij.openapi.application.PathMacroContributor
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.JpsGlobalLoader.PathVariablesSerializer
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.util.*
import java.util.concurrent.atomic.AtomicLong

@ApiStatus.Internal
@State(
  name = "PathMacrosImpl",
  category = SettingsCategory.SYSTEM,
  exportable = true,
  storages = [Storage(value = PathVariablesSerializer.STORAGE_FILE_NAME,
                      roamingType = RoamingType.DISABLED,
                      usePathMacroManager = false,
                      useSaveThreshold = ThreeState.NO
  )],
  useLoadedStateAsExisting = false,
  reportStatistic = false,
)
open class PathMacrosImpl @JvmOverloads constructor(private val loadContributors: Boolean = true) : PathMacros(), PersistentStateComponent<Element?>, ModificationTracker {
  @Volatile
  private var legacyMacros: Map<String, String> = java.util.Map.of()

  @Volatile
  private var macros: Map<String, String> = java.util.Map.of()
  private val modificationStamp = AtomicLong()
  private val ignoredMacros = ContainerUtil.createLockFreeCopyOnWriteList<String>()

  companion object {
    private val EP_NAME = ExtensionPointName<PathMacroContributor>("com.intellij.pathMacroContributor")
    private val LOG = logger<PathMacrosImpl>()

    const val IGNORED_MACRO_ELEMENT = "ignoredMacro"
    const val MAVEN_REPOSITORY = "MAVEN_REPOSITORY"

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    private val SYSTEM_MACROS: Set<String> = java.util.Set.of(
      PathMacroUtil.APPLICATION_HOME_DIR,
      PathMacroUtil.APPLICATION_PLUGINS_DIR,
      PathMacroUtil.PROJECT_DIR_MACRO_NAME,
      PathMacroUtil.MODULE_WORKING_DIR_NAME,
      PathMacroUtil.MODULE_DIR_MACRO_NAME,
      PathMacroUtil.USER_HOME_NAME,
    )

    @JvmStatic
    fun getInstanceEx(): PathMacrosImpl = getInstance() as PathMacrosImpl
  }

  override fun getUserMacroNames() = macros.keys

  override fun getUserMacros() = macros

  open fun removeToolMacroNames(result: Set<String?>) {
  }

  override fun getSystemMacroNames(): Set<String> = SYSTEM_MACROS

  override fun getIgnoredMacroNames(): Collection<String> = ignoredMacros

  override fun setIgnoredMacroNames(names: Collection<String>) {
    ignoredMacros.clear()
    ignoredMacros.addAll(names)
    modificationStamp.incrementAndGet()
  }

  override fun addIgnoredMacro(names: List<String>) {
    for (name in names) {
      if (!ignoredMacros.contains(name)) {
        ignoredMacros.add(name)
      }
    }
  }

  override fun getModificationCount() = modificationStamp.get()

  override fun isIgnoredMacroName(macro: String) = ignoredMacros.contains(macro)

  override fun getAllMacroNames(): Set<String> {
    val result = HashSet<String>(userMacroNames.size + systemMacroNames.size)
    result.addAll(userMacroNames)
    result.addAll(systemMacroNames)
    return result
  }

  override fun getValue(name: String) = macros[name]

  override fun removeAllMacros() {
    if (macros.isNotEmpty()) {
      macros = emptyMap()
      userMacroModified()
    }
  }

  override fun getLegacyMacroNames(): Collection<String> = legacyMacros.keys

  override fun setMacro(name: String, value: String?) {
    doSetMacro(name, value)
  }

  private fun doSetMacro(name: String, value: String?): Boolean {
    var macros = macros
    if (value.isNullOrBlank()) {
      if (!macros.containsKey(name)) {
        return false
      }

      macros = LinkedHashMap(macros)
      macros.remove(name)
    }
    else {
      if (macros[name] == value) {
        return false
      }

      macros = LinkedHashMap(macros)
      macros.put(name, value)
    }

    this.macros = if (macros.isEmpty()) emptyMap() else Collections.unmodifiableMap(macros)
    userMacroModified()

    return true
  }

  private fun userMacroModified() {
    modificationStamp.incrementAndGet()
  }

  override fun getState(): Element? {
    val element = Element("state")
    for ((key, value) in macros) {
      val macro = Element(PathVariablesSerializer.MACRO_TAG)
      macro.setAttribute(PathVariablesSerializer.NAME_ATTRIBUTE, key)
      macro.setAttribute(PathVariablesSerializer.VALUE_ATTRIBUTE, value)
      element.addContent(macro)
    }

    for (macro in ignoredMacros) {
      val macroElement = Element(IGNORED_MACRO_ELEMENT)
      macroElement.setAttribute(PathVariablesSerializer.NAME_ATTRIBUTE, macro)
      element.addContent(macroElement)
    }
    // temporary added to debug IDEA-256482; LOG.debug cannot be used due to IDEA-256647
    LOG.info("Saved path macros: $macros")
    return element
  }

  override fun noStateLoaded() {
    if (!loadContributors) {
      return
    }

    loadState(Element("state"))
    // https://youtrack.jetbrains.com/issue/IDEA-239124
    modificationStamp.incrementAndGet()
  }

  override fun loadState(element: Element) {
    val newMacros = linkedMapOf<String, String>()
    // register first because may be overridden by user
    val newLegacyMacros = HashMap(legacyMacros)
    EP_NAME.forEachExtensionSafe { contributor ->
      contributor.registerPathMacros(newMacros, newLegacyMacros)
    }

    for (macro in element.getChildren(PathVariablesSerializer.MACRO_TAG)) {
      val name = macro.getAttributeValue(PathVariablesSerializer.NAME_ATTRIBUTE) ?: continue
      var value = macro.getAttributeValue(PathVariablesSerializer.VALUE_ATTRIBUTE) ?: continue
      if (SYSTEM_MACROS.contains(name)) {
        continue
      }

      if (value.lastOrNull() == '/') {
        value = value.substring(0, value.length - 1)
      }
      newMacros.put(name, value)
    }

    val newIgnoredMacros = mutableListOf<String>()
    for (macroElement in element.getChildren(IGNORED_MACRO_ELEMENT)) {
      val ignoredName = macroElement.getAttributeValue(PathVariablesSerializer.NAME_ATTRIBUTE)
      if (!ignoredName.isNullOrEmpty()) {
        newIgnoredMacros.add(ignoredName)
      }
    }

    val forcedMacros = linkedMapOf<String, String>()
    EP_NAME.forEachExtensionSafe { contributor ->
      contributor.forceRegisterPathMacros(forcedMacros)
    }

    for (forcedMacro in forcedMacros) {
      if (newMacros.get(forcedMacro.key) != forcedMacro.value) {
        modificationStamp.incrementAndGet()
        break
      }
    }
    newMacros.putAll(forcedMacros)

    macros = if (newMacros.isEmpty()) java.util.Map.of() else Collections.unmodifiableMap(newMacros)
    legacyMacros = if (newLegacyMacros.isEmpty()) java.util.Map.of() else Collections.unmodifiableMap(newLegacyMacros)
    ignoredMacros.clear()
    ignoredMacros.addAll(newIgnoredMacros)
    LOG.info("Loaded path macros: $macros") //temporary added to debug IDEA-256482; LOG.debug cannot be used due to IDEA-256647
  }

  fun addMacroReplacements(result: ReplacePathToMacroMap) {
    for ((name, value) in macros) {
      result.addMacroReplacement(value, name)
    }
  }

  fun addMacroExpands(result: ExpandMacroToPathMap) {
    for ((name, value) in macros) {
      result.addMacroExpand(name, value)
    }

    for ((key, value) in legacyMacros) {
      result.addMacroExpand(key, value)
    }
  }
}