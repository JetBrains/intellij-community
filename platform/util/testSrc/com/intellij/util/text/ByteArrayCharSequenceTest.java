// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import junit.framework.TestCase;

public class ByteArrayCharSequenceTest extends TestCase {
  public void testCharactersBetween7FAndFF() {
    String s = "Straße";
    CharSequence strasse = ByteArrayCharSequence.convertToBytesIfPossible(s);
    assertTrue(strasse instanceof ByteArrayCharSequence);
    assertEquals(s, strasse.toString());
  }
}
