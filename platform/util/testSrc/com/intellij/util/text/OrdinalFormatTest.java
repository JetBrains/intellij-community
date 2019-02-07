// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.BundleBase;
import org.junit.Test;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;

public class OrdinalFormatTest {
  @Test
  public void locales() {
    // the language bundle is present, but the language is not supported by the formatter - should fall back to plain decimal
    ResourceBundle fr = ResourceBundle.getBundle("com.intellij.util.text.OrdinalFormatTest$TestBundle", Locale.FRENCH);
    assertEquals("Blah blah 1 blah blah 'whatever' blah blah 33 [fr]", BundleBase.message(fr, "ordinals", 1, "whatever", 33));

    // the language bundle isn't present - should fall back to the default (i.e. English)
    ResourceBundle de = ResourceBundle.getBundle("com.intellij.util.text.OrdinalFormatTest$TestBundle", Locale.GERMAN);
    assertEquals("Blah blah 1st blah blah 'whatever' blah blah 33rd [en]", BundleBase.message(de, "ordinals", 1, "whatever", 33));
  }

  @SuppressWarnings("unused")
  public static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][]{{"ordinals", "Blah blah {0,number,ordinal} blah blah ''{1}'' blah blah {2,number,ordinal} [en]"}};
    }
  }

  @SuppressWarnings("unused")
  public static class TestBundle_fr extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][]{{"ordinals", "Blah blah {0,number,ordinal} blah blah ''{1}'' blah blah {2,number,ordinal} [fr]"}};
    }
  }
}