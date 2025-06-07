// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import junit.framework.TestCase
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

private const val MNEMONIC_ICON_PREFIX = "IconWrapperWithTooltip:BookmarkMnemonicIcon:"

private fun testIcons(iconSupplier: (BookmarkType) -> Icon) {
  for (type in BookmarkType.entries) {
    val icon = iconSupplier(type)
    when (Char.MIN_VALUE != type.mnemonic) {
      true -> assertEquals(MNEMONIC_ICON_PREFIX + type.mnemonic, icon.toString(), "unexpected #toString")
      else -> assertNotEquals(MNEMONIC_ICON_PREFIX, icon.toString(), "unexpected #toString")
    }

    assertSame(icon, iconSupplier(type), "different icon instances for the same type")
  }
}

class IconsTest : TestCase() {
  fun testIcons() = testIcons { it.icon }
  fun testGutterIcons() = testIcons { it.gutterIcon }
}
