// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.TextWithMnemonic;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TextWithMnemonicTest {
  @Test
  public void noMnemonic() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello");
    assertEquals("hello", hello.toString());
    assertTextWithoutSuffix(hello, "hello");
    assertNoMnemonic(hello);
  }

  @Test
  public void mnemonicInString() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello", 'e');
    assertEquals("h_ello", hello.toString());
    assertTextWithoutSuffix(hello, "hello");
    assertMnemonic(hello, 1, KeyEvent.VK_E, 'e');
    assertEquals("hello", TextWithMnemonic.fromPlainText("hello", '\0').toString());
  }

  @Test
  public void parse() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h&ello");
    assertEquals("h_ello", hello.toString());
    assertTextWithoutSuffix(hello, "hello");
    assertMnemonic(hello, 1, KeyEvent.VK_E, 'e');
  }

  @Test
  public void parseUnderscore() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h_ello");
    assertEquals("h_ello", hello.toString());
    assertTextWithoutSuffix(hello, "hello");
    assertMnemonic(hello, 1, KeyEvent.VK_E, 'e');
  }

  @Test
  public void parseJapanese() {
    TextWithMnemonic hello = TextWithMnemonic.parse("hello(&H)");
    assertEquals("hello(_H)", hello.toString());
    assertText(hello, "hello", "hello(H)");
    assertMnemonic(hello, 6, KeyEvent.VK_H, 'H');

    TextWithMnemonic hello2 = TextWithMnemonic.parse("hello (&H)");
    assertEquals("hello (_H)", hello2.toString());
    assertText(hello2, "hello", "hello (H)");
    assertMnemonic(hello2, 7, KeyEvent.VK_H, 'H');
  }

  @Test
  public void parseJapaneseEllipsis() {
    TextWithMnemonic hello = TextWithMnemonic.parse("hello(&H)...");
    assertEquals("hello(_H)...", hello.toString());
    assertText(hello, "hello...", "hello(H)...");
    assertMnemonic(hello, 6, KeyEvent.VK_H, 'H');

    TextWithMnemonic hello2 = TextWithMnemonic.parse("hello (&H)…");
    assertEquals("hello (_H)…", hello2.toString());
    assertText(hello2, "hello…", "hello (H)…");
    assertMnemonic(hello2, 7, KeyEvent.VK_H, 'H');

    TextWithMnemonic fromPlain = TextWithMnemonic.fromPlainText("hello...", 'x');
    assertEquals("hello(_X)...", fromPlain.toString());
    assertText(fromPlain, "hello...", "hello(X)...");
    assertMnemonic(fromPlain, 6, KeyEvent.VK_X, 'X');
  }
  
  @Test
  public void append() {
    assertEquals("H_ello world!", TextWithMnemonic.parse("H&ello").append(" world!").toString());
  }

  @Test
  public void appendWithSuffix() {
    assertEquals("Hello world!(_H)", TextWithMnemonic.parse("Hello(&H)").append(" world!").toString());
    assertEquals("Hello world! (_H)", TextWithMnemonic.parse("Hello (&H)").append(" world!").toString());
    assertEquals("Hello world!  (_H)", TextWithMnemonic.parse("Hello  (&H)").append(" world!").toString());
  }

  @Test
  public void appendWithSuffixAndEllipsis() {
    assertEquals("Hello world!(_H)...", TextWithMnemonic.parse("Hello(&H)...").append(" world!").toString());
    assertEquals("Hello world!  (_H)…", TextWithMnemonic.parse("Hello  (&H)…").append(" world!").toString());
  }
  
  @Test
  public void replaceFirst() {
    assertEquals("_Hello wonderful world!", TextWithMnemonic.parse("&Hello {0} world!").replaceFirst("{0}", "wonderful").toString());
    assertEquals("Hello wonderful _world!", TextWithMnemonic.parse("Hello {0} &world!").replaceFirst("{0}", "wonderful").toString());
    assertEquals("Hello wonderful world!(_W)", TextWithMnemonic.parse("Hello {0} world!(&W)").replaceFirst("{0}", "wonderful").toString());
  }

  private static void assertNoMnemonic(@NotNull TextWithMnemonic wrapper) {
    assertMnemonic(wrapper, -1, KeyEvent.VK_UNDEFINED, KeyEvent.CHAR_UNDEFINED);
  }

  private static void assertMnemonic(@NotNull TextWithMnemonic wrapper, int expectedIndex, int expectedCode, char expectedChar) {
    assertEquals(expectedChar, wrapper.getMnemonicChar());
    assertEquals(expectedCode, wrapper.getMnemonicCode());
    assertEquals(expectedIndex, wrapper.getMnemonicIndex());
  }

  private static void assertText(@NotNull TextWithMnemonic wrapper, @NotNull String expectedText, @NotNull String expectedTextWithSuffix) {
    assertEquals(expectedText, wrapper.getText());
    assertEquals(expectedText, wrapper.getText(false));
    assertEquals(expectedTextWithSuffix, wrapper.getText(true));
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertTextWithoutSuffix(@NotNull TextWithMnemonic wrapper, @NotNull String expectedText) {
    String text = wrapper.getText();
    assertEquals(expectedText, text);
    assertSame(text, wrapper.getText(false));
    assertSame(text, wrapper.getText(true));
  }
}
