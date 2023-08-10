// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

public class ByteArrayCharSequenceTest extends TestCase {
  public void testSubSequenceHashCode() {
    String s = "hello world";
    ByteArrayCharSequence bacs = new ByteArrayCharSequence(s.getBytes(StandardCharsets.UTF_8));
    assertEquals(bacs.hashCode(), s.hashCode());
    assertEquals(bacs.subSequence(2, 5).hashCode(), s.subSequence(2, 5).hashCode());
    assertEquals(bacs.subSequence(2, 7).subSequence(2, 4).hashCode(), s.subSequence(2, 7).subSequence(2, 4).hashCode());
  }
}
