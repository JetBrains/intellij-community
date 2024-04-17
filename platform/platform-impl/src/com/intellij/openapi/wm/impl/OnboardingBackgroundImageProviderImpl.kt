// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus.Internal
import java.net.URL

@Internal
class OnboardingBackgroundImageProviderImpl : OnboardingBackgroundImageProviderBase() {
  override fun getImageUrl(isDark: Boolean): URL? =
    if (PlatformUtils.isIntelliJ()) javaClass.getResource(if (isDark) "/images/gradientBackground-dark.svg"
                                                          else "/images/gradientBackground-light.svg")
    else null
}