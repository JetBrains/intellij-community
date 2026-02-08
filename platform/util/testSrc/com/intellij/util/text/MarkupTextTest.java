// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.MarkupText.Fragment;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class MarkupTextTest {
  @Test
  public void testCreate() {
    MarkupText markupText = MarkupText.plainText("foo");
    assertEquals("foo", markupText.toString());
    assertEquals(List.of(new Fragment("foo", MarkupText.Kind.NORMAL)), markupText.fragments());
    assertSame(MarkupText.plainText(""), MarkupText.plainText(""));
    assertSame(MarkupText.plainText(""), MarkupText.empty());
    assertSame(MarkupText.plainText(""), MarkupText.plainText("").concat("", MarkupText.Kind.GRAYED));
  }
  
  @Test
  public void testEquals() {
    MarkupText markupText1 = buildLong();
    MarkupText markupText2 = buildLong();
    assertEquals(markupText1, markupText2);
    assertEquals(markupText1.hashCode(), markupText2.hashCode());
    assertEquals(markupText1.concat("", MarkupText.Kind.NORMAL), markupText2);
    assertNotEquals(markupText1.concat("1", MarkupText.Kind.NORMAL), markupText2);
  }
  
  @Test
  public void testConcat() {
    MarkupText markupText = buildLong();
    assertEquals("Hello !!!error!!! **bold** *italic* _underlined_ ~~strikeout~~ [grayed]", markupText.concat("", MarkupText.Kind.NORMAL).toString());
    assertEquals("Hello !!!error!!! **bold** *italic* _underlined_ ~~strikeout~~ [grayed]1", markupText.concat("1", MarkupText.Kind.NORMAL).toString());
    assertEquals("Hello !!!error!!! **bold** *italic* _underlined_ ~~strikeout~~ [grayed1]", markupText.concat("1", MarkupText.Kind.GRAYED).toString());
  }
  
  @Test
  public void testIsEmpty() {
    MarkupText markupText = buildLong();
    assertFalse(markupText.isEmpty());
    assertTrue(MarkupText.plainText("").isEmpty());
  }
  
  @Test
  public void testLength() {
    MarkupText markupText = buildLong();
    assertEquals(51, markupText.length());
    assertEquals(0, MarkupText.plainText("").length());
  }

  @Test
  public void testBuilder() {
    MarkupText markupText = buildLong();
    assertEquals("Hello !!!error!!! **bold** *italic* _underlined_ ~~strikeout~~ [grayed]", markupText.toString());
    assertEquals(List.of(new Fragment("Hello ", MarkupText.Kind.NORMAL), 
                         new Fragment("error", MarkupText.Kind.ERROR),
                         new Fragment(" ", MarkupText.Kind.NORMAL),
                         new Fragment("bold", MarkupText.Kind.STRONG),
                         new Fragment(" ", MarkupText.Kind.NORMAL),
                         new Fragment("italic", MarkupText.Kind.EMPHASIZED),
                         new Fragment(" ", MarkupText.Kind.NORMAL),
                         new Fragment("underlined", MarkupText.Kind.UNDERLINED),
                         new Fragment(" ", MarkupText.Kind.NORMAL),
                         new Fragment("strikeout", MarkupText.Kind.STRIKEOUT),
                         new Fragment(" ", MarkupText.Kind.NORMAL),
                         new Fragment("grayed", MarkupText.Kind.GRAYED)
    ), markupText.fragments());
  }
  
  @Test
  public void testHighlight() {
    MarkupText text = MarkupText.plainText("Hello World!").highlightRange(6, 11, MarkupText.Kind.GRAYED);
    assertEquals("Hello [World]!", text.toString());
    text = text.highlightRange(3, 6, MarkupText.Kind.GRAYED);
    assertEquals("Hel[lo World]!", text.toString());
    assertEquals("Hel[lo ]~~Wor~~[ld]!", text.highlightRange(6, 9, MarkupText.Kind.STRIKEOUT).toString());
    assertEquals("Hel~~lo ~~[World]!", text.highlightRange(3, 6, MarkupText.Kind.STRIKEOUT).toString());
    assertEquals("Hel[lo Wo]~~rld~~!", text.highlightRange(8, 11, MarkupText.Kind.STRIKEOUT).toString());
  }
  
  @Test
  public void testHighlightAll() {
    assertSame(MarkupText.empty().highlightAll(MarkupText.Kind.STRONG), MarkupText.empty());
    assertEquals("**hello**", MarkupText.plainText("hello").highlightAll(MarkupText.Kind.STRONG).toString());
  }
  
  @Test
  public void testToText() {
    assertEquals("Hello error bold italic underlined strikeout grayed", buildLong().toText());
  }
  
  @Test
  public void testToHtml() {
    HtmlChunk chunk = buildLong().toHtmlChunk();
    assertEquals("Hello <span class=\"error\">error</span> <b>bold</b> <i>italic</i> <u>underlined</u> <s>strikeout</s> <span class=\"grayed\">grayed</span>", 
                 chunk.toString());
  }

  private static @NotNull MarkupText buildLong() {
    MarkupText markupText = MarkupText.builder()
      .append("Hello")
      .append(" ")
      .append("error", MarkupText.Kind.ERROR)
      .append(" ")
      .append("bo", MarkupText.Kind.STRONG)
      .append("ld", MarkupText.Kind.STRONG)
      .append(" ")
      .append("")
      .append(new Fragment("italic", MarkupText.Kind.EMPHASIZED))
      .append(" ")
      .append("underlined", MarkupText.Kind.UNDERLINED)
      .append(" ")
      .append("strikeout", MarkupText.Kind.STRIKEOUT)
      .append(" ")
      .append("grayed", MarkupText.Kind.GRAYED)
      .build();
    return markupText;
  }
}
