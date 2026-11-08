// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.ide.startup.impl.STARTUP_UI_CLASSES_PATH
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WarmUpClassListTest {
  @Test
  fun `the parser splits the list at the markers and skips comments`() {
    val list = WarmUpClassList.parse(sequenceOf(
      "# a comment", WarmUpClassList.INITIALIZE_MARKER, "a.First", "", "  a.Second  ",
      WarmUpClassList.LOAD_ONLY_MARKER, "# another comment", "a.Third",
      WarmUpClassList.PLUGIN_MARKER, "b.Fourth",
    ))
    assertThat(list.initialize).containsExactly("a.First", "a.Second")
    assertThat(list.loadOnly).containsExactly("a.Third")
    assertThat(list.plugin).containsExactly("b.Fourth")
  }

  @Test
  fun `the classes before the first marker are load-only`() {
    val list = WarmUpClassList.parse(sequenceOf("a.First", "# a comment", "a.Second", WarmUpClassList.PLUGIN_MARKER, "b.Third"))
    assertThat(list.initialize).isEmpty()
    assertThat(list.loadOnly).containsExactly("a.First", "a.Second")
    assertThat(list.plugin).containsExactly("b.Third")
  }

  @Test
  fun `an absent list file gives an empty list`() {
    val list = WarmUpClassList.read(javaClass.classLoader, "com/intellij/openapi/editor/impl/warmup/absent.txt")
    assertThat(list).isSameAs(WarmUpClassList.EMPTY)
  }

  @Test
  fun `the startup UI list leaves out the editor classes`() {
    val startupUi = WarmUpClassList.read(javaClass.classLoader, STARTUP_UI_CLASSES_PATH)
    assertThat(startupUi.loadOnly).isNotEmpty()
    assertThat(startupUi.plugin).isNotEmpty()
    val editorClasses = (EDITOR_CLASSES.initialize + EDITOR_CLASSES.loadOnly).toHashSet()
    assertThat((startupUi.initialize + startupUi.loadOnly + startupUi.plugin).filter { it in editorClasses }).isEmpty()
  }
}
