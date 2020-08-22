// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.SwingHelper;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Irina.Chernushina on 1/25/2016.
 */
public class TextTruncationTest {
  @Test
  public void testVariants() {
    final MyWidthCalculator calculator = new MyWidthCalculator();
    testString("this is just a test", "this is just a test", calculator);
    testString("this is just a test", "this is just a ", calculator);
    testString("dark side of the moon", "dark side of th", calculator);
    testString("my width calculator", "my", calculator);
  }

  private static void testString(final String string, final String truncated, MyWidthCalculator calculator) {
    int width = calculator.stringWidth(truncated);
    final boolean equals = string.equals(truncated);
    if (equals) {
      width += calculator.stringWidth(SwingHelper.ERROR_STR);
    }
    else {
      width += calculator.stringWidth(SwingHelper.ELLIPSIS) + calculator.stringWidth(SwingHelper.ERROR_STR);
    }
    final String result = SwingHelper.truncateStringWithEllipsis(string, width, calculator);
    if (equals) Assert.assertEquals(truncated, result);
    else Assert.assertEquals(truncated + SwingHelper.ELLIPSIS, result);
  }

  private static final class MyWidthCalculator implements SwingHelper.WidthCalculator {
    private final Map<Character, Integer> myMap;
    private final static int DEFAULT = 3;

    private MyWidthCalculator() {
      myMap = new HashMap<>();

      final String alphabet = "abcdefghijklmnopqrstuvwxyz ";
      for (int i = 0; i < alphabet.length(); i++) {
        if (i % 3 == 0) myMap.put(alphabet.charAt(i), 5);
        myMap.put(alphabet.charAt(i), 4);
      }
    }

    @Override
    public int stringWidth(String s) {
      int result = 0;
      for (int i = 0; i < s.length(); i++) {
        result += charWidth(s.charAt(i));
      }
      return result;
    }

    @Override
    public int charWidth(char c) {
      final Integer integer = myMap.get(c);
      return integer == null ? DEFAULT : integer;
    }
  }
}
