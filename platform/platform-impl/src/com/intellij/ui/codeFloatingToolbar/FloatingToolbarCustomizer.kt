// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use lang.floatingToolbar instead")
private val DEPRECATED_EP = LanguageExtension<FloatingToolbarCustomizer>("com.intellij.lang.floatingToolbarCustomizer")
private val EP: ExtensionPointName<FloatingToolbarLanguageBean> = ExtensionPointName.create("com.intellij.lang.floatingToolbar")

private const val FLOATING_CODE_TOOLBAR_GROUP_ID = "Floating.CodeToolbar"

private fun forLanguage(language: Language): FloatingToolbarLanguageBean? {
  return EP.extensionList.firstOrNull { bean -> bean.language == language.id }
}

internal fun findActionGroupFor(language: Language): String? {
  val bean = forLanguage(language)
  if (bean != null) {
    if (bean.isMinimal) {
      // check if any of the primary languages have full toolbar available
      val hasPrimaryToolbar = IdeLanguageCustomization.getInstance().primaryIdeLanguages.any {
        val bean = forLanguage(it)
        bean != null && !bean.isMinimal
      }

      if (!hasPrimaryToolbar) return null
    }

    val customization = bean.getCustomization()
    if (customization != null && !customization.isToolbarAvailable()) {
      return null
    }

    return FLOATING_CODE_TOOLBAR_GROUP_ID
  }

  return DEPRECATED_EP.allForLanguage(language)
    .firstNotNullOfOrNull { it.getActionGroup() }
}

internal fun hasMinimalFloatingToolbar(language: Language): Boolean {
  return forLanguage(language)?.isMinimal == true
}

@ApiStatus.Experimental
interface FloatingToolbarCustomization {
  fun isToolbarAvailable(): Boolean
}

internal class FloatingToolbarLanguageBean : PluginAware {
  private var pluginDescriptor: PluginDescriptor? = null

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  @Attribute("language")
  @RequiredElement
  var language: String? = null

  @Attribute("minimal")
  var isMinimal: Boolean = false

  @Attribute("customizationClass")
  var customizationClass: String? = null

  fun getCustomization(): FloatingToolbarCustomization? {
    if (customizationClass == null) return null
    return ApplicationManager.getApplication().instantiateClass(customizationClass!!, pluginDescriptor!!)
  }
}

/**
 * Extension point to configure and enable floating code toolbar for the specific language.
 *
 * @see CodeFloatingToolbar
 * @see [FloatingToolbarCustomizer.DefaultGroup]
 */
@ApiStatus.Experimental
@Deprecated("Use lang.floatingToolbar instead")
interface FloatingToolbarCustomizer {
  /**
   * @return id of the action group to be shown in the toolbar, or null if the toolbar shouldn't be available
   */
  fun getActionGroup(): String?

  @ApiStatus.Experimental
  @Deprecated("Use lang.floatingToolbar instead")
  class DefaultGroup : FloatingToolbarCustomizer {
    override fun getActionGroup(): String = FLOATING_CODE_TOOLBAR_GROUP_ID
  }
}