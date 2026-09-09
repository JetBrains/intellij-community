// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginComponentRegistrationTest {
  @Test
  fun `ordinary rows remain registered`() {
    assertThat(
      shouldRegisterNonMarketplaceComponent(
        installing = false,
        registeredInLegacyInstallingGroup = false,
        registerInstallingWithoutGroup = false,
      )
    ).isTrue()
  }

  @Test
  fun `legacy installing rows require their group`() {
    assertThat(
      shouldRegisterNonMarketplaceComponent(
        installing = true,
        registeredInLegacyInstallingGroup = false,
        registerInstallingWithoutGroup = false,
      )
    ).isFalse()
    assertThat(
      shouldRegisterNonMarketplaceComponent(
        installing = true,
        registeredInLegacyInstallingGroup = true,
        registerInstallingWithoutGroup = false,
      )
    ).isTrue()
  }

  @Test
  fun `unified installing rows register without a legacy group`() {
    assertThat(
      shouldRegisterNonMarketplaceComponent(
        installing = true,
        registeredInLegacyInstallingGroup = false,
        registerInstallingWithoutGroup = true,
      )
    ).isTrue()
  }
}
