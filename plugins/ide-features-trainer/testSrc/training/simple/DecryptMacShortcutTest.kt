// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.simple

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import training.util.KeymapUtil

class DecryptMacShortcutTest : UsefulTestCase() {
  fun testSimpleKey() {
    TestCase.assertEquals("F1", KeymapUtil.decryptMacShortcut("F1"))
    TestCase.assertEquals("Space", KeymapUtil.decryptMacShortcut("Space"))
  }

  fun testOneModifier() {
    TestCase.assertEquals("Control + A", KeymapUtil.decryptMacShortcut("⌃A"))
    TestCase.assertEquals("Command + A", KeymapUtil.decryptMacShortcut("⌘A"))
    TestCase.assertEquals("Option + A", KeymapUtil.decryptMacShortcut("⌥A"))
    TestCase.assertEquals("Shift + F1", KeymapUtil.decryptMacShortcut("⇧F1"))
    TestCase.assertEquals("Control + 1", KeymapUtil.decryptMacShortcut("⌃1"))
    TestCase.assertEquals("Control + Space", KeymapUtil.decryptMacShortcut("⌃Space"))
  }

  fun testTwoModifier() {
    TestCase.assertEquals("Control + Shift + Space", KeymapUtil.decryptMacShortcut("⌃⇧Space"))
  }

  fun testTreeModifier() {
    TestCase.assertEquals("Control + Option + Shift + Space", KeymapUtil.decryptMacShortcut("⌃⌥⇧Space"))
  }

  fun testFindActionUsed() {
    TestCase.assertEquals("Shift + Command + A → Show Context Actions", KeymapUtil.decryptMacShortcut("⇧⌘A → Show Context Actions"))
  }
}