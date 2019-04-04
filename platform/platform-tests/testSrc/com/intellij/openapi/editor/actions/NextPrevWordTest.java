// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testNextWordAtGreaterThanEqualOperator() {
    myFixture.configureByText("Foo.java", "class Foo { boolean b = 1 <caret>>= 2; }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    myFixture.checkResult("class Foo { boolean b = 1 >= <caret>2; }");
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
