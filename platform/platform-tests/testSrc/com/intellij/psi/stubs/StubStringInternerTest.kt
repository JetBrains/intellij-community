// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StubStringInternerTest {
  @Test
  fun test() {
    val interner = StubStringInterner()
    val s = interner.apply("a")!!
    val s1 = s.encodeToByteArray().decodeToString()
    assertThat(s).isNotSameAs(s1)
    assertThat(s).isSameAs(interner.apply(s1))
  }
}