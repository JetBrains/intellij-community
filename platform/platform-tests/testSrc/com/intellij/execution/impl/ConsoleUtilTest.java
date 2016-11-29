/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 07/19/2011
 */
public class ConsoleUtilTest {

  private static final ConsoleViewContentType IMPORTANT_OUTPUT = new ConsoleViewContentType("IMPORTANT_OUTPUT", (TextAttributes)null);
  private static final ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", (TextAttributes)null);
  
  private List<ConsoleViewImpl.TokenInfo> myTokens;

  @Before
  public void setUp() {
    myTokens = new ArrayList<>();
  }
  
  @Test
  public void completeRemoval() {
    register(NORMAL_OUTPUT, 12);
    register(IMPORTANT_OUTPUT, 5);
    doTest(0, 17);
  }
  
  @Test
  public void removeLaysTooFarToTheRight() {
    register(NORMAL_OUTPUT, 12);
    register(IMPORTANT_OUTPUT, 5);
    doTest(17, 20, myTokens.toArray(new ConsoleViewImpl.TokenInfo[myTokens.size()]));
  }

  @Test
  public void removeFromSingleToken() {
    register(NORMAL_OUTPUT, 12);
    register(IMPORTANT_OUTPUT, 5);
    doTest(2, 4, t(NORMAL_OUTPUT, 0, 10), t(IMPORTANT_OUTPUT, 10, 15));
  }

  @Test
  public void removeExactTokenOffset() {
    register(NORMAL_OUTPUT, 12);
    register(IMPORTANT_OUTPUT, 5);
    doTest(12, 17, t(NORMAL_OUTPUT, 0, 12));
  }

  @Test
  public void removeInsideSingleTokenWithStrictLeft() {
    register(NORMAL_OUTPUT, 12);
    register(IMPORTANT_OUTPUT, 5);
    doTest(0, 11, t(NORMAL_OUTPUT, 0, 1), t(IMPORTANT_OUTPUT, 1, 6));
  }
  
  private void doTest(int startRemoveOffset, int endRemoveOffset, ConsoleViewImpl.TokenInfo ... expected) {
    ConsoleUtil.updateTokensOnTextRemoval(myTokens, startRemoveOffset, endRemoveOffset);
    String message = String.format("Expected: %s, actual: %s", Arrays.toString(expected), myTokens);
    assertEquals(message, expected.length, myTokens.size());
    for (int i = 0; i < expected.length; i++) {
      ConsoleViewImpl.TokenInfo expectedToken = expected[i];
      final ConsoleViewImpl.TokenInfo actualToken = myTokens.get(i);
      assertEquals(message, expectedToken.contentType, actualToken.contentType);
      assertEquals(message, expectedToken.startOffset, actualToken.startOffset);
      assertEquals(message, expectedToken.endOffset, actualToken.endOffset);
    }
  }

  private void register(ConsoleViewContentType contentType, int length) {
    int startOffset = myTokens.isEmpty() ? 0 : myTokens.get(myTokens.size() - 1).endOffset;
    int endOffset = startOffset + length;
    myTokens.add(t(contentType, startOffset, endOffset));
  }
  
  private static ConsoleViewImpl.TokenInfo t(@NotNull ConsoleViewContentType contentType, int start, int end) {
    return new ConsoleViewImpl.TokenInfo(contentType, start, end);
  }
}
