// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThreeState
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class TrustedProjectsTest {

  @Test
  fun `prefer closest ancestor to determine the trusted state`() {
    val projects = Path.of("projects/")
    val outerDir = Path.of("projects/outer")
    val innerDir = Path.of("projects/outer/inner")

    TrustedProjects.setProjectTrusted(projects, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(outerDir, false)
    Assertions.assertFalse(TrustedProjects.isProjectTrusted(innerDir))
    TrustedProjects.setProjectTrusted(innerDir, true)
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(innerDir))
  }

  @Test
  fun `return unsure if there are no information about ancestors`() {
    val projectRoot1 = Path.of("project/root1/")
    val projectRoot2 = Path.of("project/root2/")
    TrustedProjects.setProjectTrusted(projectRoot1, true)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot1))
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot2))
  }
}