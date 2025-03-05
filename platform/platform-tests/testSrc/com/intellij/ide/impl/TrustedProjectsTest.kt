// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.ThreeState
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Path

class TrustedProjectsTest {

  @Test
  fun `prefer closest ancestor to determine the trusted state`() {
    val projects = Path.of("projects/")
    val outerDir = Path.of("projects/outer")
    val innerDir = Path.of("projects/outer/inner")

    TrustedPaths.getInstance().setProjectPathTrusted(projects, true)
    assertTrusted(innerDir)
    TrustedPaths.getInstance().setProjectPathTrusted(outerDir, false)
    assertNotTrusted(innerDir)
    TrustedPaths.getInstance().setProjectPathTrusted(innerDir, true)
    assertTrusted(innerDir)
  }

  @Test
  fun `return unsure if there are no information about ancestors`() {
    val projects = Path.of("projects/")
    TrustedPaths.getInstance().setProjectPathTrusted(projects, true)

    val path = Path.of("path/")
    assertEquals(ThreeState.UNSURE, TrustedPaths.getInstance().getProjectPathTrustedState(path))
  }

  private fun assertTrusted(path: Path) = assertEquals(ThreeState.YES, TrustedPaths.getInstance().getProjectPathTrustedState(path))
  private fun assertNotTrusted(path: Path) = assertEquals(ThreeState.NO, TrustedPaths.getInstance().getProjectPathTrustedState(path))

  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }
}