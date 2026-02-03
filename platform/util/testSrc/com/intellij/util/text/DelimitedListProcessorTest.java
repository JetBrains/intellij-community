/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.DelimitedListProcessor;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DelimitedListProcessorTest extends TestCase {

  public void testProcessor() {
    doTest("a; ; ", Arrays.asList("a", " ", " "));
  }

  private void doTest(final String text, final List<String> expected) {
    final ArrayList<String> tokens = new ArrayList<>();
    new DelimitedListProcessor(";") {
      @Override
      protected void processToken(final int start, final int end, final boolean delimitersOnly) {
        tokens.add(text.substring(start, end));
      }
    }.processText(text);
    assertEquals(expected, tokens);
  }
}
