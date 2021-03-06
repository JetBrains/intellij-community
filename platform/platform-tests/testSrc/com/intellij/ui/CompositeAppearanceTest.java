// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import junit.framework.TestCase;

import java.awt.*;

import static org.junit.Assert.assertNotEquals;

public class CompositeAppearanceTest extends TestCase {
  private static final TextAttributes ATTRIBUTES = new TextAttributes(null, null, null, EffectType.BOXED, Font.PLAIN);

  public void testGetText() {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.getEnding().addText("abc", ATTRIBUTES);
    assertEquals("abc", appearance.getText());
    appearance.getEnding().addText("comment", ATTRIBUTES);
    assertEquals("abccomment", appearance.getText());
    appearance.getEnding().addText("text", ATTRIBUTES);
    assertEquals("abccommenttext", appearance.getText());
    appearance.getBeginning().addText("123", ATTRIBUTES);
    assertEquals("123abccommenttext", appearance.getText());
  }

  public void testEquals() {
    CompositeAppearance appearance1 = new CompositeAppearance();
    CompositeAppearance appearance2 = new CompositeAppearance();
    checkEquals(appearance1, appearance2);
    appearance1.getEnding().addText("abc", ATTRIBUTES);
    assertNotEquals(appearance1, appearance2);
    appearance2.getEnding().addText("abc", ATTRIBUTES);
    checkEquals(appearance1, appearance2);
    appearance1.getEnding().addText("123", new TextAttributes(null, null, null, null, Font.PLAIN));
    assertNotEquals(appearance1, appearance2);
    appearance2.getEnding().addText("123", ATTRIBUTES);
    assertNotEquals(appearance1, appearance2);
  }

  public void testSuffix() {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.getEnding().addText("a", ATTRIBUTES);
    appearance.getSuffix().addText("c", ATTRIBUTES);
    assertEquals("ac", appearance.getText());
    appearance.getEnding().addText("b", ATTRIBUTES);
    assertEquals("abc", appearance.getText());
    appearance.getBeginning().addText("0", ATTRIBUTES);
    assertEquals("0abc", appearance.getText());
  }

  public void testInsertTextAtStart() {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.getEnding().addText("c", ATTRIBUTES);
    appearance.getBeginning().addText("b", ATTRIBUTES);
    assertEquals("bc", appearance.getText());
    appearance.getSuffix().addText("d", ATTRIBUTES);
    assertEquals("bcd", appearance.getText());
    appearance.getBeginning().addText("a", ATTRIBUTES);
    assertEquals("abcd", appearance.getText());
  }

  public void testAppendText() {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.getEnding().addText(null, (TextAttributes)null);
    assertEquals(0, appearance.getText().length());
    appearance.getEnding().addText(null, (TextAttributes)null);
    assertEquals(0, appearance.getText().length());
  }

  private static void checkEquals(CompositeAppearance appearance1, CompositeAppearance appearance2) {
    assertEquals(appearance1, appearance2);
    assertEquals(appearance1.hashCode(), appearance2.hashCode());
  }
}
