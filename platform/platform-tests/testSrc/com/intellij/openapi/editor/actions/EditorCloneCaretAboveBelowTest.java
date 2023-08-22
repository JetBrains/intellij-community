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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class EditorCloneCaretAboveBelowTest extends BasePlatformTestCase {
  public void testStoringDesiredXPosition() {
    init("""
           long line<caret>
           line
           long line
           very long line""");
    cloneCaretBelow();
    checkResult("""
                  long line<caret>
                  line<caret>
                  long line
                  very long line""");
    cloneCaretBelow();
    checkResult("""
                  long line<caret>
                  line<caret>
                  long line<caret>
                  very long line""");
    cloneCaretBelow();
    checkResult("""
                  long line<caret>
                  line<caret>
                  long line<caret>
                  very long<caret> line""");
  }

  public void testCloneAndMove() {
    init("""
           long line<caret>
           line
           long line""");
    cloneCaretBelow();
    moveCaretDown();
    checkResult("""
                  long line
                  line<caret>
                  long line<caret>""");
  }

  public void testCloneBelowAndAbove() {
    init("""
           line
           <caret>li<caret>ne
           line""");
    cloneCaretBelow();
    checkResult("""
                  line
                  <caret>li<caret>ne
                  <caret>li<caret>ne""");
    cloneCaretAbove();
    checkResult("""
                  line
                  <caret>li<caret>ne
                  line""");
    cloneCaretAbove();
    checkResult("""
                  <caret>li<caret>ne
                  <caret>li<caret>ne
                  line""");
    cloneCaretBelow();
    checkResult("""
                  line
                  <caret>li<caret>ne
                  line""");
  }

  public void testCloneWithSelection() {
    init("""
           long <selection>line<caret></selection>
           line
           long line""");
    cloneCaretBelow();
    checkResult("""
                  long <selection>line<caret></selection>
                  line
                  long <selection>line<caret></selection>""");
  }

  private void cloneCaretBelow() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW);
  }

  private void cloneCaretAbove() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE);
  }

  private void moveCaretDown() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }

  private void init(String text) {
    myFixture.configureByText(getTestName(false) + ".txt", text);
  }

  private void checkResult(String text) {
    myFixture.checkResult(text);
  }
}
