// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import java.net.URL

internal class IdeaDialogBackgroundImageProvider : DialogBackgroundImageProviderBase() {
  override fun getImageUrl(isDark: Boolean, isIslands: Boolean): URL? {
    return javaClass.getResource(if (isDark) {
      if (isIslands) "/images/gradientBackground-islands-dark.svg" else "/images/gradientBackground-dark.svg"
    } else {
      "/images/gradientBackground-light.svg"
    })
  }
}