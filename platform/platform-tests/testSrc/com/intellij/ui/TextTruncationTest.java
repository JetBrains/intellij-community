/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  private static class MyWidthCalculator implements SwingHelper.WidthCalculator {
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
