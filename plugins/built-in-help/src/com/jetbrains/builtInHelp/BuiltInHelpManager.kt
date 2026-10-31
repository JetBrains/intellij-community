// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.help.HelpTarget
import com.intellij.openapi.help.HelpTargetProvider
import com.jetbrains.builtInHelp.settings.SettingsPage
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.builtInWebServer.BuiltInServerOptions

val LOG: Logger = Logger.getInstance(BuiltInHelpManager::class.java)

class BuiltInHelpManager : HelpTargetProvider {
  override fun getHelpTarget(helpId: String): HelpTarget? {
    if (javaClass.classLoader.getResource("topics/Map.jhm") == null) return null
    val defaultUrl = HelpManagerImpl.getDefaultHelpUrl(helpId)
    val keymap = defaultUrl?.let { QueryStringDecoder(it).parameters()["keymap"]?.firstOrNull() }
    val locale = LocalizationStateService.getInstance()?.selectedLocale.orEmpty()
    return HelpTarget(
      HelpUrls.entryUrl(BuiltInServerOptions.getInstance().effectiveBuiltInServerPort, helpId, keymap, locale),
      SettingsPage.getSelectedBrowserId(),
    )
  }
}
