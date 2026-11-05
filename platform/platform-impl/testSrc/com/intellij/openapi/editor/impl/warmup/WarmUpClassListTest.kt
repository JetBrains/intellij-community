// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WarmUpClassListTest {
  @Test
  fun `the parser splits the list at the load-only marker and skips comments`() {
    val list = WarmUpClassList.parse(sequenceOf(
      "# a comment", "a.First", "", "  a.Second  ", WarmUpClassList.LOAD_ONLY_MARKER, "# another comment", "a.Third",
    ))
    assertThat(list.initialize).containsExactly("a.First", "a.Second")
    assertThat(list.loadOnly).containsExactly("a.Third")
  }

  @Test
  fun `a list without the marker has no load-only classes`() {
    val list = WarmUpClassList.parse(sequenceOf("a.First", "# a comment", "a.Second"))
    assertThat(list.initialize).containsExactly("a.First", "a.Second")
    assertThat(list.loadOnly).isEmpty()
  }

  @Test
  fun `an absent list file gives an empty list`() {
    val list = WarmUpClassList.read(javaClass.classLoader, "com/intellij/openapi/editor/impl/warmup/absent.txt")
    assertThat(list).isSameAs(WarmUpClassList.EMPTY)
  }
}
