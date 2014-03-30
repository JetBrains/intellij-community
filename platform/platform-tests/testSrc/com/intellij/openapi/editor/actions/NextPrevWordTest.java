/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class NextPrevWordTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testNextWordFromPreLastPosition() {
    myFixture.configureByText("a.txt", "<foo<caret>>");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("<foo><caret>");
  }

  public void testPrevWordFrom1() {
    myFixture.configureByText("a.txt", "<<caret>foo>");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("<caret><foo>");
  }

}
