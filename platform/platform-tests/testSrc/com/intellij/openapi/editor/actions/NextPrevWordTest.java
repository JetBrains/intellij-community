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
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.testFramework.EditorTestUtil;
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

  public void testNextWordWithEscapeChars() {
    myFixture.configureByText("Foo.java", "class Foo { String s = \"a\\<caret>nb\"; }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("class Foo { String s = \"a\\n<caret>b\"; }");
  }

  public void testPrevWordWithEscapeChars() {
    myFixture.configureByText("Foo.java", "class Foo { String s = \"a\\nb<caret>\"; }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("class Foo { String s = \"a\\n<caret>b\"; }");
  }

  public void testPrevWordWithIllegalEscapeChar() {
    myFixture.configureByText("Foo.java", "class Foo { String s = \"a\\xb<caret>\"; }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("class Foo { String s = \"a\\x<caret>b\"; }");
  }

  public void testPrevNextWordWithFolding() {
    myFixture.configureByText("a.txt", "<caret>brown fox");
    EditorTestUtil.addFoldRegion(myFixture.getEditor(), 4, 7, "...", true);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("brow<caret>n fox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("brown f<caret>ox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("brown fox<caret>");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("brown f<caret>ox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("brow<caret>n fox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    myFixture.checkResult("<caret>brown fox");
    FoldRegion[] foldRegions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    assertEquals(1, foldRegions.length);
    assertFalse(foldRegions[0].isExpanded());
  }

  public void testPrevNextWordWithSelectionAndFolding() {
    myFixture.configureByText("a.txt", "<caret>brown fox");
    EditorTestUtil.addFoldRegion(myFixture.getEditor(), 4, 7, "...", true);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION);
    myFixture.checkResult("<selection>brow<caret></selection>n fox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION);
    myFixture.checkResult("<selection>brown f<caret></selection>ox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION);
    myFixture.checkResult("<selection>brown fox<caret></selection>");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION);
    myFixture.checkResult("<selection>brown f<caret></selection>ox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION);
    myFixture.checkResult("<selection>brow<caret></selection>n fox");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION);
    myFixture.checkResult("<caret>brown fox");
    FoldRegion[] foldRegions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    assertEquals(1, foldRegions.length);
    assertFalse(foldRegions[0].isExpanded());
  }
  
  public void testRegexpCharsAreNotTreatedAsSeparateWords() {
    FileType regExpFileType = FileTypeManagerEx.getInstanceEx().findFileTypeByName("RegExp");
    assertNotNull(regExpFileType);
    myFixture.configureByText(regExpFileType, "<caret>abc");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("abc<caret>");
  }
}
