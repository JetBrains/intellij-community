// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThreeState
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TrustedProjectsCheckDisablingTest {

  @Test
  fun `the headless bypass trusts everything, including an explicitly untrusted project`() {
    val projectRoot = Path.of("project/headless-bypass")

    Assertions.assertTrue(TrustedProjects.isTrustedCheckDisabled()) { "The bypass this test is about is not in effect" }
    TrustedProjects.setProjectTrusted(projectRoot, false)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(projectRoot))
  }

  @Test
  @SystemProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, "false")
  fun `a test that opts out of the headless bypass sees the explicitly recorded state`() {
    val projectRoot = Path.of("project/headless-opt-out")

    Assertions.assertFalse(TrustedProjects.isTrustedCheckDisabled()) { "The opt-out this test is about is not in effect" }
    Assertions.assertEquals(ThreeState.UNSURE, TrustedProjects.getProjectTrustedState(projectRoot))

    TrustedProjects.setProjectTrusted(projectRoot, false)
    Assertions.assertEquals(ThreeState.NO, TrustedProjects.getProjectTrustedState(projectRoot))
    Assertions.assertFalse(TrustedProjects.isProjectTrusted(projectRoot))
  }

  @Test
  @SystemProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, "false")
  @SystemProperty("idea.trust.disabled", "true")
  fun `a product with a trusted check of its own overrules an explicitly untrusted project`() {
    val projectRoot = Path.of("project/product-flag")

    TrustedProjects.setProjectTrusted(projectRoot, false)
    Assertions.assertEquals(ThreeState.YES, TrustedProjects.getProjectTrustedState(projectRoot))
    Assertions.assertTrue(TrustedProjects.isProjectTrusted(projectRoot))
  }
}
