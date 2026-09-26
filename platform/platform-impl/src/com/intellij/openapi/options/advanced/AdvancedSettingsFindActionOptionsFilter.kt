// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

import com.intellij.BundleBase
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.gotoByName.FindActionSearchableOptionsFilter
import java.util.Locale

internal const val ADVANCED_SETTINGS_CONFIGURABLE_ID: String = "advanced.settings"

internal fun AdvancedSettingBean.isApplicable(): Boolean {
  return when {
    id == "project.view.do.not.autoscroll.to.libraries" -> !PlatformUtils.isDataGrip()
    else -> true
  }
}

internal class AdvancedSettingsFindActionOptionsFilter : FindActionSearchableOptionsFilter {
  override fun isAvailable(description: OptionDescription): Boolean {
    if (description.configurableId != ADVANCED_SETTINGS_CONFIGURABLE_ID) {
      return true
    }

    val hit = description.hit ?: return true
    val normalizedHit = normalizeAdvancedSettingSearchText(hit)
    val advancedSettingsTitle = normalizeAdvancedSettingSearchText(ApplicationBundle.message("title.advanced.settings"))
    return normalizedHit == advancedSettingsTitle || availableAdvancedSettingsSearchTexts().contains(normalizedHit)
  }

  private fun availableAdvancedSettingsSearchTexts(): Set<String> {
    val result = HashSet<String>()
    for (extension in AdvancedSettingBean.EP_NAME.extensionList) {
      if (!extension.isApplicable() || !extension.isVisible()) {
        continue
      }

      result.addNormalized(extension.id)
      result.addNormalized(extension.title())
      result.addNormalized(extension.group() ?: ApplicationBundle.message("group.advanced.settings.other"))
      result.addNormalized(extension.description())
      result.addNormalized(extension.trailingLabel())
      extension.enumKlass?.enumConstants?.forEach {
        result.addNormalized(it.toString())
      }
      result.addNormalized(ApplicationBundle.message("button.advanced.settings.reset"))
    }
    return result
  }
}

private fun MutableSet<String>.addNormalized(text: String?) {
  if (text.isNullOrBlank()) {
    return
  }
  add(normalizeAdvancedSettingSearchText(text))
}

private fun normalizeAdvancedSettingSearchText(text: String): String {
  return StringUtil.unescapeXmlEntities(text)
    .replace(BundleBase.MNEMONIC_STRING, "")
    .trim()
    .removeSuffix(":")
    .trim()
    .lowercase(Locale.ROOT)
}
