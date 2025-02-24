package com.intellij.settingsSync.core

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentCategorizer
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.settingsSync.core.config.EDITOR_FONT_SUBCATEGORY_ID
import java.util.concurrent.ConcurrentHashMap

internal fun isSyncCategoryEnabled(fileSpec: String): Boolean {
  val rawFileSpec = removeOsPrefix(fileSpec)
  if (rawFileSpec == SettingsSyncSettings.FILE_SPEC)
    return true

  val (category, subCategory) = getSchemeCategory(rawFileSpec) ?: getRoamableCategory(rawFileSpec) ?: return false

  if (category != SettingsCategory.OTHER && SettingsSyncSettings.getInstance().isCategoryEnabled(category)) {
    if (subCategory != null) {
      return SettingsSyncSettings.getInstance().isSubcategoryEnabled(category, subCategory)
    }

    return true
  }
  return false
}

private fun removeOsPrefix(fileSpec: String): String {
  val osPrefix = getPerOsSettingsStorageFolderName() + "/"
  return if (fileSpec.startsWith(osPrefix)) fileSpec.removePrefix(osPrefix) else fileSpec
}

private fun getRoamableCategory(fileName: String, componentClasses: List<Class<PersistentStateComponent<Any>>>): Pair<SettingsCategory, String?> {
  for (componentClass in componentClasses) {
    val category = ComponentCategorizer.getCategory(componentClass)
    if (category == SettingsCategory.OTHER) continue

    val state = componentClass.getAnnotation(State::class.java) ?: continue
    val storage = state.storages.find { it.value == fileName }
    if (storage == null) {
      if (state.additionalExportDirectory != fileName && !fileName.startsWith(state.additionalExportDirectory + '/')) {
        continue
      }
    } else if(!storage.roamingType.isRoamable) {
      continue
    }

    // Once found, ignore any other possibly conflicting definitions
    return (category to getSubCategory(fileName))
  }


  return SettingsCategory.OTHER to null
}

private val roamambleCategoryCache: ConcurrentHashMap<String, Pair<SettingsCategory, String?>?> = ConcurrentHashMap()

fun getRoamableCategory(fileName: String): Pair<SettingsCategory, String?>? {
  roamambleCategoryCache[fileName]?.let { cachedCategory ->
    return cachedCategory
  }

  val componentClasses = findComponentClasses(fileName)
  if (componentClasses.isEmpty()) {
    // classes are not yet loaded or not available on that IDE. Ignore that file
    return null
  }

  val category = getSchemeCategory(fileName) ?: getRoamableCategory(fileName, componentClasses)

  roamambleCategoryCache[fileName] = category

  return category
}

private fun getSchemeCategory(fileSpec: String): Pair<SettingsCategory, String?>? {
  // fileSpec is e.g. keymaps/mykeymap.xml
  val separatorIndex = fileSpec.indexOf("/")
  val directoryName = if (separatorIndex >= 0) fileSpec.substring(0, separatorIndex) else fileSpec  // e.g. 'keymaps'

  var settingsCategory: SettingsCategory? = null
  (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    if (it.fileSpec == directoryName) {
      settingsCategory = it.getSettingsCategory()
    }
  }
  if (settingsCategory == null) {
    return null
  }

  return settingsCategory to null
}


private fun getSubCategory(fileSpec: String): String? {
  if (fileSpec == AppEditorFontOptions.STORAGE_NAME)
    return EDITOR_FONT_SUBCATEGORY_ID
  else
    return null
}

private fun findComponentClasses(fileSpec: String): List<Class<PersistentStateComponent<Any>>> {
  val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
  val componentClasses = ArrayList<Class<PersistentStateComponent<Any>>>()
  componentManager.processAllImplementationClasses { aClass, _ ->
    if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
      val state = aClass.getAnnotation(State::class.java) ?: return@processAllImplementationClasses
      if (state.additionalExportDirectory.isNotEmpty() && (fileSpec == state.additionalExportDirectory || fileSpec.startsWith(state.additionalExportDirectory + "/")) ||
          state.storages.any { storage -> !storage.deprecated && storage.value == fileSpec }) {
        @Suppress("UNCHECKED_CAST")
        componentClasses.add(aClass as Class<PersistentStateComponent<Any>>)
      }
    }
  }
  return componentClasses
}