// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.ThreeState
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.div

class TrustedProjectsTest {

  @JvmField @Rule val memoryFs = InMemoryFsRule()

  @Test fun `prefer closest ancestor to determine the trusted state`() {
    val projects = memoryFs.fs.getPath("~/projects/")
    val outerDir = (projects / "outer")
    val innerDir = (outerDir / "inner")

    TrustedPaths.getInstance().setProjectPathTrusted(projects, true)
    assertTrusted(innerDir)
    TrustedPaths.getInstance().setProjectPathTrusted(outerDir, false)
    assertNotTrusted(innerDir)
    TrustedPaths.getInstance().setProjectPathTrusted(innerDir, true)
    assertTrusted(innerDir)
  }

  @Test fun `return unsure if there are no information about ancestors`() {
    val projects = memoryFs.fs.getPath("~/projects/")
    TrustedPaths.getInstance().setProjectPathTrusted(projects, true)

    val path = memoryFs.fs.getPath("~/path/")
    assertEquals(ThreeState.UNSURE, TrustedPaths.getInstance().getProjectPathTrustedState(path))
  }

  private fun assertTrusted(path: Path) = assertEquals(ThreeState.YES, TrustedPaths.getInstance().getProjectPathTrustedState(path))
  private fun assertNotTrusted(path: Path) = assertEquals(ThreeState.NO, TrustedPaths.getInstance().getProjectPathTrustedState(path))

  companion object {
    @ClassRule @JvmField val appRule = ApplicationRule()
  }
}