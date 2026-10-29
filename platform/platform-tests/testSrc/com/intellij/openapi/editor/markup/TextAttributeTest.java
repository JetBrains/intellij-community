// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.idea.TestFor;
import com.intellij.openapi.util.InvalidDataException;
import junit.framework.TestCase;
import org.jdom.Element;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("UseJBColor")
public class TextAttributeTest extends TestCase {

  public void testMergeDefaultAttributes() {
    TextAttributes attributes = new TextAttributes();
    TextAttributes otherAttributes = new TextAttributes();
    TextAttributes merge = TextAttributes.merge(attributes, otherAttributes);
    assertEquals(attributes, merge);
  }

  public void testMergeCoversColorsAndKeepsEffectsOfBothLayers() {
    Color red = new Color(0xFF0000);
    Color blue = new Color(0x0000FF);
    TextAttributes under = new TextAttributes(red, null, red, EffectType.LINE_UNDERSCORE, Font.BOLD);
    TextAttributes above = new TextAttributes(null, blue, blue, EffectType.STRIKEOUT, Font.ITALIC);

    TextAttributes merge = TextAttributes.merge(under, above);

    assertEquals(red, merge.getForegroundColor());
    assertEquals(blue, merge.getBackgroundColor());
    assertEquals(Font.BOLD | Font.ITALIC, merge.getFontType());
    assertEquals(Map.of(EffectType.LINE_UNDERSCORE, red, EffectType.STRIKEOUT, blue), allEffects(merge));
  }

  public void testMergeWithoutEffectsResetsTheEffectType() {
    TextAttributes under = new TextAttributes(null, null, null, null, Font.PLAIN);
    TextAttributes above = new TextAttributes(new Color(0x00FF00), null, null, null, Font.PLAIN);

    TextAttributes merge = TextAttributes.merge(under, above);

    assertEquals(EffectType.BOXED, merge.getEffectType());
    assertNull(merge.getEffectColor());
    assertTrue(allEffects(merge).isEmpty());
  }

  public void testApplyToSetsEffectsOfTheBuilder() {
    Color red = new Color(0xFF0000);
    Color blue = new Color(0x0000FF);
    TextAttributes attributes = new TextAttributes(blue, null, null, null, Font.BOLD);

    TextAttributesEffectsBuilder.create().coverWith(EffectType.WAVE_UNDERSCORE, red).coverWith(EffectType.BOXED, blue).applyTo(attributes);

    assertEquals(blue, attributes.getForegroundColor());
    assertEquals(Font.BOLD, attributes.getFontType());
    assertEquals(Map.of(EffectType.WAVE_UNDERSCORE, red, EffectType.BOXED, blue), allEffects(attributes));
  }

  /**
   * A subclass can override the setters, so {@link TextAttributesEffectsBuilder#applyTo} must call them.
   */
  public void testApplyToCallsTheSettersOfASubclass() {
    TextAttributesEffectsBuilder effects = TextAttributesEffectsBuilder.create().coverWith(EffectType.WAVE_UNDERSCORE, new Color(0xFF0000));
    try {
      effects.applyTo(new UnmodifiableTextAttributes());
      fail("UnmodifiableTextAttributes must reject the effects");
    }
    catch (UnsupportedOperationException ignored) {
    }
  }

  public void testMergeTakesFontFamiliesOfAboveOverThoseOfUnder() {
    FontFamilies mono = FontFamilies.of("JetBrains Mono");
    FontFamilies serif = FontFamilies.of("Noto Serif");

    assertEquals(mono, TextAttributes.merge(withFontFamilies(mono), new TextAttributes()).getFontFamilies());
    assertEquals(serif, TextAttributes.merge(new TextAttributes(), withFontFamilies(serif)).getFontFamilies());
    assertEquals(serif, TextAttributes.merge(withFontFamilies(mono), withFontFamilies(serif)).getFontFamilies());
  }

  public void testApplyToKeepsFontFamilies() {
    TextAttributes attributes = withFontFamilies(FontFamilies.of("JetBrains Mono"));

    TextAttributesEffectsBuilder.create().coverWith(EffectType.WAVE_UNDERSCORE, new Color(0xFF0000)).applyTo(attributes);

    assertEquals(FontFamilies.of("JetBrains Mono"), attributes.getFontFamilies());
  }

  public void testFontFamiliesMakeAttributesNotEmpty() {
    assertTrue(new TextAttributes().isEmpty());
    assertFalse(withFontFamilies(FontFamilies.of("JetBrains Mono")).isEmpty());
  }

  public void testWriteAndReadFontFamiliesInElement() {
    TextAttributes attributes = withFontFamilies(FontFamilies.of("JetBrains Mono", "Noto Sans CJK JP"));
    assertEquals("JetBrains Mono,Noto Sans CJK JP", writeAndReadField(attributes, "FONT_FAMILIES"));

    Element element = new Element("value");
    attributes.writeExternal(element);

    assertEquals(attributes, new TextAttributes(element));
  }

  public void testReadWrongFontFamiliesFromElementFails() {
    Element element = new Element("value");
    addField(element, "FONT_FAMILIES", "JetBrains Mono,,Noto Serif");
    try {
      TextAttributes attributes = new TextAttributes(element);
      fail("an empty font family name must be rejected, but the result is " + attributes);
    }
    catch (InvalidDataException ignored) {
    }
  }

  public void testWriteAndReadFontFamiliesInBinaryData() throws IOException {
    TextAttributes attributes = new TextAttributes(new Color(0x112233), null, null, null, Font.BOLD);
    attributes.setFontFamilies(FontFamilies.of("JetBrains Mono", "Noto Sans CJK JP"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    attributes.writeExternal(new DataOutputStream(bytes));
    TextAttributes restored = new TextAttributes(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

    assertEquals(attributes, restored);
  }

  public void testSetAttributesResetsFontFamilies() {
    TextAttributes attributes = withFontFamilies(FontFamilies.of("JetBrains Mono"));

    attributes.setAttributes(new Color(0x112233), null, null, null, null, Font.PLAIN);

    assertNull(attributes.getFontFamilies());
  }

  public void testCopyFromAndCloneKeepFontFamilies() {
    TextAttributes attributes = withFontFamilies(FontFamilies.of("JetBrains Mono"));
    TextAttributes copy = new TextAttributes();

    copy.copyFrom(attributes);

    assertEquals(attributes.getFontFamilies(), copy.getFontFamilies());
    assertEquals(attributes.getFontFamilies(), attributes.clone().getFontFamilies());
  }

  public void testUnmodifiableTextAttributesRejectFontFamilies() {
    try {
      new UnmodifiableTextAttributes().setFontFamilies(FontFamilies.of("JetBrains Mono"));
      fail("UnmodifiableTextAttributes must reject font families");
    }
    catch (UnsupportedOperationException ignored) {
    }
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

  private static TextAttributes withFontFamilies(FontFamilies fontFamilies) {
    TextAttributes attributes = new TextAttributes();
    attributes.setFontFamilies(fontFamilies);
    return attributes;
  }

  private static Map<EffectType, Color> allEffects(TextAttributes attributes) {
    Map<EffectType, Color> effects = new EnumMap<>(EffectType.class);
    attributes.forEachEffect(effects::put);
    return effects;
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
