/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.bookmarks;

import junit.framework.TestCase;

/**
 * User: Vassiliy.Kudryashov
 */
public class BookmarkTest extends TestCase {
  public void testIcons() {
    String availableMnemonics = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    for (int i = 0; i < availableMnemonics.length(); i++) {
      char mnemonic = availableMnemonics.charAt(i);
      assertEquals(mnemonic, Bookmark.MnemonicIcon.getIcon(mnemonic).hashCode());
    }
    Bookmark.MnemonicIcon icon1 = Bookmark.MnemonicIcon.getIcon('Z');
    Bookmark.MnemonicIcon icon2 = Bookmark.MnemonicIcon.getIcon('Z');
    assert icon1 == icon2;
    icon1 = Bookmark.MnemonicIcon.getIcon((char)('Z' + 1));
    icon2 = Bookmark.MnemonicIcon.getIcon((char)('Z' + 1));
    assert icon1 != icon2;
  }
}
