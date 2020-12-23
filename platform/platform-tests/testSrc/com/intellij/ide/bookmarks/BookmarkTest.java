// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks;

import junit.framework.TestCase;

import javax.swing.Icon;

public class BookmarkTest extends TestCase {
  private static final String BOOKMARK_ICON_STRING = "IconWrapperWithTooltip:BookmarkIcon";
  private static final String MNEMONIC_ICON_PREFIX = "IconWrapperWithTooltip:MnemonicIcon:";

  public void testIcons() {
    assertEquals(BOOKMARK_ICON_STRING, Bookmark.DEFAULT_ICON.toString());
    String availableMnemonics = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    for (int i = 0; i < availableMnemonics.length(); i++) {
      char mnemonic = availableMnemonics.charAt(i);
      assertEquals(MNEMONIC_ICON_PREFIX + mnemonic, Bookmark.MnemonicIcon.getIcon(mnemonic).toString());
    }
    Icon icon1 = Bookmark.MnemonicIcon.getIcon('Z');
    Icon icon2 = Bookmark.MnemonicIcon.getIcon('Z');
    assert icon1 == icon2;
    icon1 = Bookmark.MnemonicIcon.getIcon((char)('Z' + 1));
    icon2 = Bookmark.MnemonicIcon.getIcon((char)('Z' + 1));
    assert icon1 != icon2;
  }
}
