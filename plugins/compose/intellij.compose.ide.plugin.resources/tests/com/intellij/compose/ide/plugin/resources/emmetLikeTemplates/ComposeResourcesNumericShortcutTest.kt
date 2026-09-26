// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Test

class ComposeResourcesNumericShortcutTest {

  @Test
  fun `test numeric shortcut regex matches valid inputs`() {
    assertShortcutMatch("1", "s", "1")
    assertShortcutMatch("1", "s", "1s")
    assertShortcutMatch("2", "d", "2d")
    assertShortcutMatch("12", "s", "12")
  }

}