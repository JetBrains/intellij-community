// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.BundleBase;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

public class BundleBaseTest {
  @Test
  public void testPartial() {
    ResourceBundle bundle = ResourceBundle.getBundle("com.intellij.util.text.BundleBaseTest$TestBundle");
    String template = BundleBase.partialMessage(bundle, "partial", 1, new Object[]{"User{Name}"});
    assertThat(template).isEqualTo("Hello ''User'{'Name'}''', let''s play {0}!");
    String result = MessageFormat.format(template, "a game");
    assertThat(result).isEqualTo("Hello 'User{Name}', let's play a game!");

    String template1 = BundleBase.partialMessage(bundle, "partial2", 1, new Object[]{"0'{}", "1'{}"});
    String template2 = BundleBase.partialMessage(bundle, "partial2", 2, new Object[]{"0'{}"});
    assertThat(MessageFormat.format(template1, "2'{}")).isEqualTo("Param0: 0'{}; Param1: 1'{}; Param2: 2'{}");
    assertThat(MessageFormat.format(template2, "1'{}", "2'{}")).isEqualTo("Param0: 0'{}; Param1: 1'{}; Param2: 2'{}");
  }
  
  public static final class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][]{{"partial", "Hello ''{0}'', let''s play {1}!"},
        {"partial2", "Param0: {0}; Param1: {1}; Param2: {2}"}};
    }
  }
}
