// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.eel

import com.intellij.testFramework.junit5.SystemProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EelFixtureFilterTest {
  @Test
  fun default() {
    assertEquals(EelFixtureFilter.parse(""), EelFixtureFilter(
      isLocalEelEnabled = true,
      isLocalIjentEnabled = true,
      isDockerEnabled = true,
      isWslEnabled = true,
      isSshEnabled = false,
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

  @Test
  fun ssh() {
    assertEquals(EelFixtureFilter.parse("ssh"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = false,
      isWslEnabled = false,
      isSshEnabled = true,
    ))
  }

  @Test
  fun `docker,ssh`() {
    assertEquals(EelFixtureFilter.parse("docker,ssh"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = true,
      isWslEnabled = false,
      isSshEnabled = true,
    ))
  }

  @Test
  fun `ssh does not change isDefault`() {
    assertTrue(EelFixtureFilter.parse("local,docker,wsl,ssh").isDefault)
  }

  @Test
  fun `the ssh flag takes part in equals`() {
    assertNotEquals(EelFixtureFilter(isSshEnabled = true), EelFixtureFilter(isSshEnabled = false))
  }

  @Test
  @SystemProperty("ijent.test.skip.docker", "true")
  fun `ijent test skip docker turns off ssh`() {
    assertEquals(EelFixtureFilter.parse("docker,ssh"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = false,
      isWslEnabled = false,
      isSshEnabled = false,
    ))
  }

  @Test
  fun `-docker`() {
    assertEquals(EelFixtureFilter.parse("-docker"), EelFixtureFilter(
      isLocalEelEnabled = true,
      isLocalIjentEnabled = true,
      isDockerEnabled = false,
      isWslEnabled = true,
      isSshEnabled = false,
    ))
  }

  @Test
  fun `-local`() {
    assertEquals(EelFixtureFilter.parse("-local"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = false,
      isDockerEnabled = true,
      isWslEnabled = true,
      isSshEnabled = false,
    ))
  }

  @Test
  fun `-wsl,-docker`() {
    assertEquals(EelFixtureFilter.parse("-wsl,-docker"), EelFixtureFilter(
      isLocalEelEnabled = true,
      isLocalIjentEnabled = true,
      isDockerEnabled = false,
      isWslEnabled = false,
      isSshEnabled = false,
    ))
  }

  @Test
  fun `local,-local-eel`() {
    assertEquals(EelFixtureFilter.parse("local,-local-eel"), EelFixtureFilter(
      isLocalEelEnabled = false,
      isLocalIjentEnabled = true,
      isDockerEnabled = false,
      isWslEnabled = false,
      isSshEnabled = false,
    ))
  }

  @Test
  fun `unknown negative label`() {
    assertThrows(IllegalStateException::class.java) {
      EelFixtureFilter.parse("-unknown")
    }
  }
}