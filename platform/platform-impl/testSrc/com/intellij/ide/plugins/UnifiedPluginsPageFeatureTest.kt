// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class UnifiedPluginsPageFeatureTest {
  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  fun `unified page can be enabled for the next page session`() {
    assertThat(UnifiedPluginsPageFeature.isEnabled()).isTrue()
  }

  @Test
  fun `density variant uses the baseline by default`() {
    assertThat(UnifiedPluginsPageFeature.densityVariant()).isEqualTo(UnifiedPluginsPageDensityVariant.Baseline)
  }

  @Test
  @RegistryKey(
    key = UnifiedPluginsPageFeature.DENSITY_REGISTRY_KEY,
    value = "[Baseline|32 px icons*|2 preview rows]",
  )
  fun `density variant selects 32 pixel icons`() {
    val variant = UnifiedPluginsPageFeature.densityVariant()

    assertThat(variant).isEqualTo(UnifiedPluginsPageDensityVariant.Icons32)
    assertThat(variant.pluginIconScale).isEqualTo(0.8f)
    assertThat(variant.collapsedItemLimit).isEqualTo(3)
  }

  @Test
  @RegistryKey(
    key = UnifiedPluginsPageFeature.DENSITY_REGISTRY_KEY,
    value = "[Baseline|32 px icons|2 preview rows*]",
  )
  fun `density variant selects two preview rows`() {
    val variant = UnifiedPluginsPageFeature.densityVariant()

    assertThat(variant).isEqualTo(UnifiedPluginsPageDensityVariant.TwoPreviewRows)
    assertThat(variant.pluginIconScale).isEqualTo(1.0f)
    assertThat(variant.collapsedItemLimit).isEqualTo(2)
  }
}
