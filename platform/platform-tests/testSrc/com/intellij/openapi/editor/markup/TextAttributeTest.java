// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.idea.TestFor;
import junit.framework.TestCase;
import org.jdom.Element;

import java.awt.Color;
import java.awt.Font;

@SuppressWarnings("UseJBColor")
public class TextAttributeTest extends TestCase {

  public void testMergeDefaultAttributes() {
    TextAttributes attributes = new TextAttributes();
    TextAttributes otherAttributes = new TextAttributes();
    TextAttributes merge = TextAttributes.merge(attributes, otherAttributes);
    assertEquals(attributes, merge);
  }

  public void testWriteOpaqueColorsToElement() {
    TextAttributes attributes = new TextAttributes(new Color(0xAF, 0x1D, 0x1D), new Color(0x00, 0x00, 0xFF),
                                                   null, EffectType.BOXED, Font.PLAIN);
    assertEquals("af1d1d", writeAndReadField(attributes, "FOREGROUND"));
    // opaque colors keep the historical unpadded format, so old IDEs can still read the scheme
    assertEquals("ff", writeAndReadField(attributes, "BACKGROUND"));
  }

  @TestFor(issues = "IJPL-223521")
  public void testWriteTranslucentColorsToElement() {
    TextAttributes attributes = new TextAttributes(new Color(0xAF, 0x1D, 0x1D, 0x80), new Color(0x00, 0x00, 0xFF, 0x01),
                                                   null, EffectType.BOXED, Font.PLAIN);
    assertEquals("af1d1d80", writeAndReadField(attributes, "FOREGROUND"));
    assertEquals("0000ff01", writeAndReadField(attributes, "BACKGROUND"));
  }

  @TestFor(issues = "IJPL-223521")
  public void testElementRoundTripKeepsAlpha() {
    TextAttributes attributes = new TextAttributes(new Color(0xAF, 0x1D, 0x1D, 0x80), new Color(0x1D, 0x1D, 0xAF, 0x40),
                                                   new Color(0x11, 0x22, 0x33, 0x44), EffectType.WAVE_UNDERSCORE, Font.BOLD);
    attributes.setErrorStripeColor(new Color(0x55, 0x66, 0x77, 0x88));

    TextAttributes restored = writeAndRead(attributes);
    assertEquals(attributes, restored);
    assertEquals(0x80, restored.getForegroundColor().getAlpha());
    assertEquals(0x40, restored.getBackgroundColor().getAlpha());
    assertEquals(0x44, restored.getEffectColor().getAlpha());
    assertEquals(0x88, restored.getErrorStripeColor().getAlpha());
  }

  public void testReadLegacyRgbFromElement() {
    Element element = new Element("value");
    addField(element, "FOREGROUND", "af1d1d");
    addField(element, "BACKGROUND", "ff");

    TextAttributes attributes = new TextAttributes(element);
    assertEquals(new Color(0xAF, 0x1D, 0x1D), attributes.getForegroundColor());
    assertEquals(new Color(0x00, 0x00, 0xFF), attributes.getBackgroundColor());
  }

  private static void addField(Element parent, String name, String value) {
    parent.addContent(new Element("option").setAttribute("name", name).setAttribute("value", value));
  }

  private static String writeAndReadField(TextAttributes attributes, String name) {
    Element element = new Element("value");
    attributes.writeExternal(element);
    for (Element option : element.getChildren("option")) {
      if (name.equals(option.getAttributeValue("name"))) {
        return option.getAttributeValue("value");
      }
    }
    return null;
  }

  private static TextAttributes writeAndRead(TextAttributes attributes) {
    Element element = new Element("value");
    attributes.writeExternal(element);
    return new TextAttributes(element);
  }
}
