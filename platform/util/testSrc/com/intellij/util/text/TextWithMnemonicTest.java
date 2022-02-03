// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.TextWithMnemonic;
import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;

public class TextWithMnemonicTest {
  @Test
  public void noMnemonic() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello");
    assertEquals("hello", hello.getText());
    assertEquals("hello", hello.toString());
    assertEquals(0, hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_UNDEFINED, hello.getMnemonicCode());
    assertEquals(-1, hello.getMnemonicIndex());
  }

  @Test
  public void mnemonicInString() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello", 'e');
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('e', hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_E, hello.getMnemonicCode());
    assertEquals(1, hello.getMnemonicIndex());
    assertEquals("hello", TextWithMnemonic.fromPlainText("hello", '\0').toString());
  }

  @Test
  public void parse() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h&ello");
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('e', hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_E, hello.getMnemonicCode());
    assertEquals(1, hello.getMnemonicIndex());
  }

  @Test
  public void parseUnderscore() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h_ello");
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('e', hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_E, hello.getMnemonicCode());
    assertEquals(1, hello.getMnemonicIndex());
  }

  @Test
  public void parseJapanese() {
    TextWithMnemonic hello = TextWithMnemonic.parse("hello(&H)");
    assertEquals("hello", hello.getText());
    assertEquals("hello(_H)", hello.toString());
    assertEquals('H', hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_H, hello.getMnemonicCode());
    assertEquals(6, hello.getMnemonicIndex());

    TextWithMnemonic hello2 = TextWithMnemonic.parse("hello (&H)");
    assertEquals("hello", hello2.getText());
    assertEquals("hello (_H)", hello2.toString());
    assertEquals('H', hello2.getMnemonicChar());
    assertEquals(KeyEvent.VK_H, hello2.getMnemonicCode());
    assertEquals(7, hello2.getMnemonicIndex());
  }

  @Test
  public void parseJapaneseEllipsis() {
    TextWithMnemonic hello = TextWithMnemonic.parse("hello(&H)...");
    assertEquals("hello...", hello.getText());
    assertEquals("hello(_H)...", hello.toString());
    assertEquals('H', hello.getMnemonicChar());
    assertEquals(KeyEvent.VK_H, hello.getMnemonicCode());
    assertEquals(6, hello.getMnemonicIndex());

    TextWithMnemonic hello2 = TextWithMnemonic.parse("hello (&H)…");
    assertEquals("hello…", hello2.getText());
    assertEquals("hello (_H)…", hello2.toString());
    assertEquals('H', hello2.getMnemonicChar());
    assertEquals(KeyEvent.VK_H, hello2.getMnemonicCode());
    assertEquals(7, hello2.getMnemonicIndex());

    TextWithMnemonic fromPlain = TextWithMnemonic.fromPlainText("hello...", 'x');
    assertEquals("hello...", fromPlain.getText());
    assertEquals("hello(_X)...", fromPlain.toString());
    assertEquals('X', fromPlain.getMnemonicChar());
    assertEquals(KeyEvent.VK_X, fromPlain.getMnemonicCode());
    assertEquals(6, fromPlain.getMnemonicIndex());
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
}
