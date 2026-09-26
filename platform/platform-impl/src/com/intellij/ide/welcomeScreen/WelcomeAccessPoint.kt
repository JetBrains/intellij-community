// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.welcomeScreen

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WelcomeAccessPoint {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeAccessPoint> =
      ExtensionPointName("com.intellij.platform.ide.welcomeScreenAccessPoint")

    private fun getSingleExtension(): WelcomeAccessPoint? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple WelcomeAccessPoint extensions")
        return null
      }
      return providers.first()
    }

    internal fun isAvailable(): Boolean {
      return getSingleExtension()?.isAvailable() ?: true
    }
  }

  fun isAvailable(): Boolean
}