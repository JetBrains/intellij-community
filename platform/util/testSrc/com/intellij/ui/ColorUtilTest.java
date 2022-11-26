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
package com.intellij.ui;

import junit.framework.TestCase;

import java.awt.*;

import static com.intellij.ui.ColorUtil.*;
import static java.util.Arrays.asList;

public class ColorUtilTest extends TestCase {
  public void testColorMix() {
    assertEquals(Color.GRAY, mix(Color.BLACK, Color.WHITE, .5));
    assertEquals(Color.LIGHT_GRAY, mix(Color.BLACK, Color.WHITE, .751));
    assertEquals(Color.BLACK, mix(Color.BLACK, Color.WHITE, 0));
    assertEquals(Color.WHITE, mix(Color.BLACK, Color.WHITE, 1));
    assertEquals(Color.WHITE, mix(Color.WHITE, Color.WHITE, 0));
    assertEquals(Color.WHITE, mix(Color.WHITE, Color.WHITE, .5));
    assertEquals(withAlpha(Color.GRAY, .17), mix(withAlpha(Color.WHITE, .17), withAlpha(Color.BLACK, .17), .5));
  }

  public void testColorMixWrapping() {
    assertEquals(Color.class, mix(Color.BLACK, Color.WHITE, .5).getClass());
    assertEquals(JBColor.class, mix(Color.BLACK, JBColor.WHITE, .5).getClass());
    assertEquals(JBColor.class, mix(JBColor.BLACK, JBColor.WHITE, .5).getClass());
    assertEquals(JBColor.class, mix(JBColor.BLACK, Color.WHITE, .5).getClass());
  }

  public void testColorMixOutOfRange() {
    assertSame(Color.BLACK, mix(Color.BLACK, Color.WHITE, Double.NEGATIVE_INFINITY));
    assertSame(Color.BLACK, mix(Color.BLACK, Color.WHITE, -Double.MAX_VALUE));
    assertSame(Color.BLACK, mix(Color.BLACK, Color.WHITE, -Double.MIN_VALUE));
    assertSame(Color.BLACK, mix(Color.BLACK, Color.WHITE, 0));
    assertSame(Color.WHITE, mix(Color.BLACK, Color.WHITE, 1));
    assertSame(Color.WHITE, mix(Color.BLACK, Color.WHITE, Double.MAX_VALUE));
    assertSame(Color.WHITE, mix(Color.BLACK, Color.WHITE, Double.POSITIVE_INFINITY));
  }

  public void testBlackFromHex() {
    for (String hex : asList("000", "#000", "0x000",
                             "000F", "#000F", "0x000F",
                             "000f", "#000f", "0x000f",
                             "000000", "#000000", "0x000000",
                             "000000FF", "#000000FF", "0x000000FF",
                             "000000ff", "#000000fF", "0x000000Ff")) {
      assertEquals(Color.BLACK, fromHex(hex));
    }
  }

  public void testWhiteFromHex() {
    for (String hex : asList("FFF", "#FFF", "0xFFF",
                             "FFFF", "#FFFF", "0xFFFF",
                             "ffff", "#ffFF", "0xfFfF",
                             "FFFFFF", "#FFFFFF", "0xFFFFFF",
                             "FFFFFFFF", "#FFFFFFFF", "0xFFFFFFFF",
                             "ffffffff", "#ffffFFFF", "0xfFfFfFfF")) {
      assertEquals(Color.WHITE, fromHex(hex));
    }
  }

  public void testRedFromHex() {
    for (String hex : asList("F00", "#F00", "0xF00",
                             "F00F", "#F00F", "0xF00F",
                             "FF0000", "#FF0000", "0xFF0000",
                             "FF0000FF", "#FF0000FF", "0xFF0000FF")) {
      assertEquals(Color.RED, fromHex(hex));
    }
  }

  public void testGreenFromHex() {
    for (String hex : asList("0F0", "#0F0", "0x0F0",
                             "0F0F", "#0F0F", "0x0F0F",
                             "00FF00", "#00FF00", "0x00FF00",
                             "00FF00FF", "#00FF00FF", "0x00FF00FF")) {
      assertEquals(Color.GREEN, fromHex(hex));
    }
  }

  public void testBlueFromHex() {
    for (String hex : asList("00F", "#00F", "0x00F",
                             "00FF", "#00FF", "0x00FF",
                             "0000FF", "#0000FF", "0x0000FF",
                             "0000FFFF", "#0000FFFF", "0x0000FFFF")) {
      assertEquals(Color.BLUE, fromHex(hex));
    }
  }

  public void testAlphaFromHex() {
    assertEquals(new Color(0x00000000, true), fromHex("0000"));
    assertEquals(new Color(0xAA123456, true), fromHex("123456AA"));
    assertEquals(new Color(0xAA2468AC, true), fromHex("#2468ACAA"));
  }

  public void testAlphaBlending() {
    assertEquals(new Color(48, 78, 241, 15), alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 0)));
    assertEquals(new Color(0, 0, 0, 0), alphaBlending(new Color(48, 78, 241, 0), new Color(120, 46, 97, 0)));
    assertEquals(new Color(120, 46, 97, 24), alphaBlending(new Color(48, 78, 241, 0), new Color(120, 46, 97, 24)));
    assertEquals(new Color(48, 78, 241, 255), alphaBlending(new Color(48, 78, 241, 255), new Color(120, 46, 97, 24)));
    assertEquals(new Color(115, 47, 105, 255), alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 255)));
    assertEquals(new Color(91, 58, 154, 37), alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 24)));
  }
}
