// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class GithubServerPathDataResidencyTest(
  private val host: String,
  private val isDataResidency: Boolean
) {
  companion object {
    @JvmStatic @get:Parameters
    val tests = arrayOf<Array<Any>>(
      arrayOf("github.com", false),
      arrayOf("okta.github.com", false),
      arrayOf("okta.ghe.com", true),
      arrayOf("ghe.com", true),
      arrayOf("bla.bla.com", false),
      arrayOf("bla.com", false),
      arrayOf("GHE.com", true),
      arrayOf("TENANT.GHE.COM", true),
    )
  }

  @Test
  fun testDataResidencyDetection() {
    assertEquals(isDataResidency, GithubServerPath(host).isGheDataResidency)
  }
}