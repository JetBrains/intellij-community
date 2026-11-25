// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class Java11ShimImplTest {
  private val shim = Java11ShimImpl()

  @Test
  fun `the caller class is the class below the callee frames`() {
    assertThat(Callee.callerClass(shim)).isEqualTo(Java11ShimImplTest::class.java)
  }

  @Test
  fun `the caller class without a callee is the caller of the shim`() {
    assertThat(shim.findCallerClass { false }).isEqualTo(Java11ShimImplTest::class.java)
  }

  private object Callee {
    fun callerClass(shim: Java11ShimImpl): Class<*>? = inner(shim)

    private fun inner(shim: Java11ShimImpl): Class<*>? = shim.findCallerClass { it === Callee::class.java }
  }
}
