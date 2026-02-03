package com.intellij.settingsSync.core.config

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SettingsCategory.*
import com.intellij.settingsSync.core.SettingsSyncState
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import org.jetbrains.annotations.Nls
import java.util.*

internal class SyncCategoryHolder(val descriptor: Category) {

  private var state: SettingsSyncState? = null

  var isSynchronized: Boolean = state?.isCategoryEnabled(descriptor.category) ?: false

  val name: @Nls String
    get() = descriptor.name

  val description: @Nls String
    get() = descriptor.description

  val secondaryGroup: SyncSubcategoryGroup?
    get() = descriptor.secondaryGroup

  fun reset() {
    with(descriptor) {
      isSynchronized = state?.isCategoryEnabled(category) ?: false
      if (secondaryGroup != null) {
        secondaryGroup.getDescriptors().forEach {
          it.isSelected = isSynchronized && state?.isSubcategoryEnabled(category, it.id) ?: false
        }
      }
    }
  }

  fun apply() {
    with(descriptor) {
      if (secondaryGroup != null) {
        secondaryGroup.getDescriptors().forEach {
          // !isSynchronized not store disabled states individually
          state?.setSubcategoryEnabled(category, it.id, !isSynchronized || it.isSelected)
        }
      }
      state?.setCategoryEnabled(category, isSynchronized)
    }
  }

  fun isModified(): Boolean {
    with(descriptor) {
      if (isSynchronized != state?.isCategoryEnabled(category)) return true
      if (secondaryGroup != null && isSynchronized) {
        secondaryGroup.getDescriptors().forEach {
          if (it.isSelected != state?.isSubcategoryEnabled(category, it.id)) return true
        }
      }
      return false
    }
  }

  override fun toString(): String {
    return "SyncCategoryHolder(name='$name', isSynchronized=$isSynchronized, isModified=${isModified()})"
  }


  companion object {
    val allHolders: List<SyncCategoryHolder> = arrayListOf<SyncCategoryHolder>().apply {
      for (descriptor in Category.DESCRIPTORS) {
        add(SyncCategoryHolder(descriptor))
      }
    }

    fun updateState(state: SettingsSyncState) {
      allHolders.forEach {
        it.state = state
      }
    }

    val disabledCategories: List<SettingsCategory>
      get() = arrayListOf<SettingsCategory>().apply {
        for (holder in allHolders) {
          if (!holder.isSynchronized) {
            add(holder.descriptor.category)
          }
        }
      }

    val disabledSubcategories: Map<SettingsCategory, List<String>>
      get() = hashMapOf<SettingsCategory, MutableList<String>>().apply {
        for (holder in allHolders) {
          val descriptors = holder.secondaryGroup?.getDescriptors() ?: continue
          for (descriptor in descriptors) {
            if (!descriptor.isSelected) {
              computeIfAbsent(holder.descriptor.category) { arrayListOf() }.add(descriptor.id)
            }
          }
        }
      }
  }

  internal class Category(
    val category: SettingsCategory,
    val secondaryGroup: SyncSubcategoryGroup? = null,
  ) {

    val name: @Nls String
      get() {
        return message("${categoryKey}.name")
      }

    val description: @Nls String
      get() {
        return message("${categoryKey}.description")
      }

    private val categoryKey: String
      get() {
        return "settings.category." + category.name.lowercase(Locale.getDefault())
      }

    companion object {
      internal val DESCRIPTORS: List<Category> = listOf(
        Category(UI, SyncUiGroup()),
        Category(KEYMAP),
        Category(CODE),
        Category(PLUGINS, SyncPluginsGroup()),
        Category(TOOLS),
        Category(SYSTEM),
      )
    }
  }

}