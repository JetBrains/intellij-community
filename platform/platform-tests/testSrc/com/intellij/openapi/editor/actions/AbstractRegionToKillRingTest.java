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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 4/19/11 6:32 PM
 */
public abstract class AbstractRegionToKillRingTest extends LightPlatformCodeInsightTestCase {

  public void testNoSelection() throws Exception {
    doTest("this is a test string");
  }

  public void testSingleLineSelection() throws Exception {
    doTest("this is a t<selection>est str</selection>ing");
  }

  public void testMultiLineSelection() throws Exception {
    doTest(
      "this is the fir<selection>st string\n" +
      "this is the sec</selection>ond string"
    );
  }
  
  protected abstract void doTest(@NotNull String text) throws Exception;

  /**
   * Checks current editor and returns tuple of <code>(selected text; text over than selected)</code>.
   * 
   * @return    tuple of <code>(selected text; text over than selected)</code>.
   */
  @NotNull
  protected static Pair<String, String> parse() {
    SelectionModel selectionModel = myEditor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return new Pair<>(null, myEditor.getDocument().getText());
    }
    
    CharSequence text = myEditor.getDocument().getCharsSequence();
    String selectedText = text.subSequence(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()).toString();
    StringBuilder nonSelectedText = new StringBuilder();
    nonSelectedText.append(text.subSequence(0, selectionModel.getSelectionStart()))
      .append(text.subSequence(selectionModel.getSelectionEnd(), text.length()));
    return new Pair<>(selectedText, nonSelectedText.toString());
  }
}
