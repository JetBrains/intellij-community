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

  /**
   * Reading is deliberately not gated on {@code editor.text.attributes.transparency}: a scheme saved while the key
   * was on must stay readable after it is turned off again.
   */
  @TestFor(issues = "IJPL-223521")
  public void testReadColorsWithAlphaFromElement() {
    Element element = new Element("value");
    addField(element, "FOREGROUND", "af1d1d80");
    addField(element, "BACKGROUND", "1d1daf40");
    addField(element, "EFFECT_COLOR", "11223344");
    addField(element, "ERROR_STRIPE_COLOR", "55667788");

    TextAttributes attributes = new TextAttributes(element);
    assertEquals(new Color(0xAF, 0x1D, 0x1D, 0x80), attributes.getForegroundColor());
    assertEquals(new Color(0x1D, 0x1D, 0xAF, 0x40), attributes.getBackgroundColor());
    assertEquals(new Color(0x11, 0x22, 0x33, 0x44), attributes.getEffectColor());
    assertEquals(new Color(0x55, 0x66, 0x77, 0x88), attributes.getErrorStripeColor());
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

}
