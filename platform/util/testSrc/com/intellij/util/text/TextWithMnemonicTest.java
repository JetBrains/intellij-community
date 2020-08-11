// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.TextWithMnemonic;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextWithMnemonicTest {
  @Test
  public void noMnemonic() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello");
    assertEquals("hello", hello.getText());
    assertEquals("hello", hello.toString());
    assertEquals(0, hello.getMnemonic());
    assertEquals(-1, hello.getMnemonicIndex());
  }

  @Test
  public void mnemonicInString() {
    TextWithMnemonic hello = TextWithMnemonic.fromPlainText("hello", 'e');
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('E', hello.getMnemonic());
    assertEquals(1, hello.getMnemonicIndex());
  }

  @Test
  public void parse() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h&ello");
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('E', hello.getMnemonic());
    assertEquals(1, hello.getMnemonicIndex());
  }

  @Test
  public void parseUnderscore() {
    TextWithMnemonic hello = TextWithMnemonic.parse("h_ello");
    assertEquals("hello", hello.getText());
    assertEquals("h_ello", hello.toString());
    assertEquals('E', hello.getMnemonic());
    assertEquals(1, hello.getMnemonicIndex());
  }

  @Test
  public void parseJapanese() {
    TextWithMnemonic hello = TextWithMnemonic.parse("hello(&H)");
    assertEquals("hello", hello.getText());
    assertEquals("hello(_H)", hello.toString());
    assertEquals('H', hello.getMnemonic());
    assertEquals(6, hello.getMnemonicIndex());
  }
  
  @Test
  public void append() {
    assertEquals("H_ello world!", TextWithMnemonic.parse("H&ello").append(" world!").toString());
    assertEquals("Hello _world!(H)", TextWithMnemonic.parse("Hello(&H)").append(" world!").toString());
  }
  
  @Test
  public void replaceFirst() {
    assertEquals("_Hello wonderful world!", TextWithMnemonic.parse("&Hello {0} world!").replaceFirst("{0}", "wonderful").toString());
    assertEquals("Hello wonderful _world!", TextWithMnemonic.parse("Hello {0} &world!").replaceFirst("{0}", "wonderful").toString());
    assertEquals("Hello wonderful world!(_W)", TextWithMnemonic.parse("Hello {0} world!(&W)").replaceFirst("{0}", "wonderful").toString());
  }
}
