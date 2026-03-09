// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Locale
import java.util.ResourceBundle

class DynamicBundleLocaleCacheTest {
  @BeforeEach
  fun setUp() {
    DynamicBundle.clearCache()
    ResourceBundle.clearCache(javaClass.classLoader)
  }

  @AfterEach
  fun tearDown() {
    DynamicBundle.clearCache()
    ResourceBundle.clearCache(javaClass.classLoader)
  }

  @Test
  fun `explicit locale lookups are cached per locale`() {
    val loader = javaClass.classLoader

    val englishBundle = DynamicBundle.getResourceBundle(loader, BUNDLE_NAME, Locale.ENGLISH)
    val russianBundle = DynamicBundle.getResourceBundle(loader, BUNDLE_NAME, Locale.forLanguageTag("ru"))

    assertThat(englishBundle.getString(MESSAGE_KEY)).isEqualTo("en")
    assertThat(russianBundle.getString(MESSAGE_KEY)).isEqualTo("ru")
  }

  @ParameterizedTest
  @ValueSource(strings = ["ru", "en"])
  fun `default locale lookup does not poison explicit locale cache`(localeVariant: String) {
    val loader = javaClass.classLoader
    val locale = Locale.forLanguageTag(localeVariant)

    DynamicBundle.getResourceBundle(loader, BUNDLE_NAME)
    val explicitBundle = DynamicBundle.getResourceBundle(loader, BUNDLE_NAME, locale)

    assertThat(explicitBundle.getString(MESSAGE_KEY)).isEqualTo(localeVariant)
  }

  companion object {
    private const val BUNDLE_NAME = "com.intellij.DynamicBundleLocaleCacheTestBundle"
    private const val MESSAGE_KEY = "value"
  }
}
