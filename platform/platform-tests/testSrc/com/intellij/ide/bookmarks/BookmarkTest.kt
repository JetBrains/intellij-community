// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks

import junit.framework.TestCase
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertSame

private const val BOOKMARK_ICON_STRING = "IconWrapperWithTooltip:BookmarkIcon"
private const val MNEMONIC_ICON_PREFIX = "IconWrapperWithTooltip:MnemonicIcon:"

private fun testIcons(iconSupplier: (BookmarkType) -> Icon) = BookmarkType.values().forEach {
  val icon = iconSupplier(it)
  val expected = when (Char.MIN_VALUE != it.mnemonic) {
    true -> MNEMONIC_ICON_PREFIX + it.mnemonic
    else -> BOOKMARK_ICON_STRING
  }
  assertEquals(expected, icon.toString(), "unexpected #toString")
  assertSame(icon, iconSupplier(it), "different icon instances for the same type")
}

class BookmarkTest : TestCase() {
  fun testIcons() = testIcons { it.icon }
  fun testGutterIcons() = testIcons { it.gutterIcon }
}
