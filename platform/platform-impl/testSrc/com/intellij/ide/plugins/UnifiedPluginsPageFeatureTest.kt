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
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  @RegistryKey(key = UnifiedPluginsPageFeature.STANDALONE_DIALOG_REGISTRY_KEY, value = "true")
  fun `standalone dialog is available when both switches are enabled`() {
    assertThat(UnifiedPluginsPageFeature.isStandaloneDialogEnabled()).isTrue()
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "true")
  @RegistryKey(key = UnifiedPluginsPageFeature.STANDALONE_DIALOG_REGISTRY_KEY, value = "false")
  fun `standalone dialog switch restores the Settings route`() {
    assertThat(UnifiedPluginsPageFeature.isStandaloneDialogEnabled()).isFalse()
  }

  @Test
  @RegistryKey(key = UnifiedPluginsPageFeature.REGISTRY_KEY, value = "false")
  @RegistryKey(key = UnifiedPluginsPageFeature.STANDALONE_DIALOG_REGISTRY_KEY, value = "true")
  fun `legacy page does not use the standalone entry point`() {
    assertThat(UnifiedPluginsPageFeature.isStandaloneDialogEnabled()).isFalse()
  }

  @Test
  fun `density variant uses 32 pixel icons by default`() {
    assertThat(UnifiedPluginsPageFeature.densityVariant()).isEqualTo(UnifiedPluginsPageDensityVariant.Icons32)
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
    assertThat(variant.compactRows).isTrue()
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
    assertThat(variant.compactRows).isFalse()
  }
}
