// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.ProductPluginInitContext
import com.intellij.ide.plugins.loadDescriptorsFromCustomPluginDir
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

internal object Localization242 {
  private val log: Logger
    get() = Logger.getInstance(Localization242::class.java)

  fun enableL10nIfPluginInstalled(previousVersion: String?, oldPluginsDir: Path) {
    if (previousVersion == null || StringUtil.compareVersionNumbers(previousVersion, "2024.2") != -1) {
      log.info("[i18n] Localization migration won't be performed because previous version is $previousVersion")
      return
    }

    @Suppress("RAW_RUN_BLOCKING")
    val loadedDescriptors = runBlocking {
      val pluginList = loadDescriptorsFromCustomPluginDir(oldPluginsDir, true)
      val initContext = ProductPluginInitContext()
      pluginList.plugins.filter { !initContext.isPluginDisabled(it.pluginId) }
    }

    val localizationPlugins = loadedDescriptors.filter { descriptor -> isLocalizationPlugin(descriptor) }
    if (localizationPlugins.isEmpty()) {
      log.info("[i18n] Localization migration won't be performed because no localization plugins were found")
      return
    }
    localizationPlugins.firstNotNullOfOrNull { getLanguageTagFromDescriptor(it) }?.let {
      if (LocalizationStateService.getInstance() != null) {
        LocalizationStateService.getInstance()!!.setSelectedLocale(it, true)
        log.info("[i18n] Locale is set to $it in LocalizationStateService")
      }
      if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
        EarlyAccessRegistryManager.setString("i18n.locale", it)
        EarlyAccessRegistryManager.syncAndFlush()
        log.info("[i18n] Locale is set to $it in Registry")
      }
      else {
        EarlyAccessRegistryManager.setAndFlush(mapOf("i18n.locale" to it))
        log.info("[i18n] Locale is set to $it in EarlyAccessRegistryManager")
      }
      log.info("[i18n] Localization migration was performed with language tag $it")
    } ?: log.info("[i18n] Localization migration won't be performed because language tag was not found")
  }


  /**
   * Checks if the given [IdeaPluginDescriptor] is a localization plugin.
   *
   * @param descriptor the descriptor of the plugin to be checked
   * @return `true` if the plugin is a localization plugin; `false` otherwise
   */
  private fun isLocalizationPlugin(descriptor: IdeaPluginDescriptor): Boolean {
    if (descriptor !is IdeaPluginDescriptorImpl) return false
    val extensionPoints = descriptor.extensions
    val epName = "com.intellij.languageBundle"
    return extensionPoints.containsKey(epName)
  }

  private fun getLanguageTagFromDescriptor(descriptor: IdeaPluginDescriptor): String? {
    return if (isLocalizationPlugin(descriptor)) {
      val extensionPoints = (descriptor as IdeaPluginDescriptorImpl).extensions
      val epName = "com.intellij.languageBundle"
      extensionPoints[epName]?.firstOrNull()?.element?.attributes?.get("locale")
    }
    else {
      null
    }
  }
}
