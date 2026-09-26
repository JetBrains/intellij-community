// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.eel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EelFixtureFilterTest {
  @Test
  fun default() {
    assertEquals(EelFixtureFilter.parse(""), EelFixtureFilter(
      isLocalEelEnabled = true,
      isLocalIjentEnabled = true,
      isDockerEnabled = true,
      isWslEnabled = true
    ))
  }

  @Test
  fun `local-eel`() {
    assertEquals(EelFixtureFilter.parse("local-eel"), EelFixtureFilter(
      isLocalEelEnabled = true,
      isLocalIjentEnabled = false,
      isDockerEnabled = false,
      isWslEnabled = false
    ))
  }

  @Test
  fun `local-ijent`() {
    assertEquals(EelFixtureFilter.parse("local-ijent"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = true,
      isDockerEnabled = false,
      isWslEnabled = false
    ))
  }

  @Test
  fun docker() {
    assertEquals(EelFixtureFilter.parse("docker"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = true,
      isWslEnabled = false
    ))
  }

  @Test
  fun wsl() {
    assertEquals(EelFixtureFilter.parse("wsl"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = false,
      isWslEnabled = true
    ))
  }

  @Test
  fun `wsl,docker`() {
    assertEquals(EelFixtureFilter.parse("wsl,docker"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = true,
      isWslEnabled = true
    ))
  }
}