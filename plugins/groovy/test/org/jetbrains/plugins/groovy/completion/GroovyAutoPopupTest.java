// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

import java.util.List;

public class GroovyAutoPopupTest extends JavaCompletionAutoPopupTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testGenerallyFocusLookup() {
    myFixture.configureByText("a.groovy", """
      
              String foo(String xxxxxx) {
                return xx<caret>
              }
          \
      """);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.doHighlighting());
    type("x");
    assertTrue(getLookup().isFocused());
  }

  public void testTopLevelFocus() {
    myFixture.configureByText("a.groovy", "<caret>");
    type("p");
    assertTrue(getLookup().isFocused());
  }

  public void testNoLookupFocusOnUnresolvedQualifier() {
    myFixture.configureByText("a.groovy", """
      xxx.<caret>""");
    type("h");//hashCode
    assertNull(getLookup());
  }

  public void testNoLookupFocusOnUntypedQualifier() {
    myFixture.configureByText("a.groovy", """
      
            def foo(xxx) {
              xxx.<caret>
            }\
      """);
    type("h");
    assertNull(getLookup());
  }

  public void testImpossibleClosureParameter() {
    myFixture.configureByText("a.groovy", "String a; { a.<caret> }");
    type("h");
    assertTrue(getLookup().isFocused());
  }

  public void testFieldTypeLowercase() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.groovy", "class Foo { <caret> }");
    type("aioobe");
    assertEquals(myFixture.getLookupElementStrings(), List.of(ArrayIndexOutOfBoundsException.class.getSimpleName()));
  }

  public void testNoWordCompletionAutoPopup() {
    myFixture.configureByText("a.groovy", "def foo = \"f<caret>\"");
    type("o");
    assertNull(getLookup());
  }

  public void testClassesAndPackagesInUnqualifiedImports() {
    myFixture.addClass("package Xxxxx; public class Xxxxxxxxx {}");
    myFixture.configureByText("a.groovy", "package foo; import <caret>");
    type("Xxx");
    assertEquals(myFixture.getLookupElementStrings(), List.of("Xxxxxxxxx", "Xxxxx"));
  }

  public void testPopupAfterDotAfterPackage() {
    myFixture.configureByText("a.groovy", "<caret>");
    type("import jav");
    assertNotNull(getLookup());
    type(".");
    assertNotNull(getLookup());
  }

  public void testTypingFirstVarargDot() {
    myFixture.addClass("class Foo { static class Bar {} }");
    myFixture.configureByText("a.groovy", "void foo(Foo<caret>[] a) { }");
    type(".");
    assertNotNull(getLookup());
    type(".");
    myFixture.checkResult("void foo(Foo..<caret>[] a) { }");
  }

  public void testTypingFirstVarargDot2() {
    myFixture.addClass("class Foo { static class Bar {} }");
    myFixture.configureByText("a.groovy", "void foo(Foo<caret>) { }");
    type(".");
    assertNotNull(getLookup());
    type(".");
    myFixture.checkResult("void foo(Foo..<caret>) { }");
  }

  public void testDotDot() {
    myFixture.configureByText("a.groovy", "2<caret>");
    type(".");
    assertNotNull(getLookup());
    assertTrue(getLookup().isFocused());
    type(".");
    assertNull(getLookup());

    myFixture.checkResult("2..<caret>");
  }

  public void testInsideClosure() {
    myFixture.configureByText("a.groovy", "def cl = { foo(); <caret> }");
    type("h");
    assertNotNull(getLookup());
    assertTrue(getLookup().isFocused());
  }

  public void testNonImportedClass() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.configureByText("a.groovy", "<caret>");
    type("Abcde ");
    myFixture.checkResult("import foo.Abcdefg\n\nAbcdefg <caret>");
  }

  public void test_two_non_imported_classes_when_space_does_not_select_first_autopopup_item() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.groovy", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
    EdtTestUtil.runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    type(" ");
    myFixture.checkResult("""
                            import foo.Abcdefg
                            
                            class Foo extends Abcdefg <caret>""");
  }

  public void testTwoNonImportedClasses() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.groovy", "<caret>");
    type("Abcde ");
    myFixture.checkResult("""
                            import bar.Abcdefg
                            
                            Abcdefg <caret>""");
  }

  public void testPrivate() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.groovy", "class Foo { <caret> }");
    type("pri");
    assertEquals("private", myFixture.getLookupElementStrings().get(0));
  }

  public void testFieldTypeNonImported() {
    myFixture.addClass("package foo; public class PrimaBalerina {}");
    myFixture.configureByText("a.groovy", "class Foo { <caret> }");
    type("PrimaB");
    assertEquals(myFixture.getLookupElementStrings(), List.of("PrimaBalerina"));
  }

  public void testEnteringLabel() {
    myFixture.configureByText("a.groovy", "<caret>");
    type("FIS:");
    assertEquals("FIS:", myFixture.getEditor().getDocument().getText());
  }

  public void testEnteringNamedArg() {
    myFixture.configureByText("a.groovy", "foo(<caret>)");
    type("has:");
    myFixture.checkResult("foo(has:<caret>)");
  }

  public void testEnteringMapKey() {
    myFixture.configureByText("a.groovy", "[<caret>]");
    type("has:");
    myFixture.checkResult("[has:<caret>]");
  }

  public void testPreferRightCasedVariant() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.groovy", "<caret>");
    type("boo");
    myFixture.assertPreferredCompletionItems(0, "boolean");
    type("\b\b\bBoo");
    myFixture.assertPreferredCompletionItems(0, "Boolean");
  }

  public void testPackageQualifier() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package com.too; public class Util {}");
    myFixture.configureByText("a.groovy", "void foo(Object command) { <caret> }");
    type("com.t");
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("too", "command.toString")));
  }

  public void testVarargParenthesis() {
    myFixture.configureByText("a.groovy", """
      
      void foo(File... files) { }
      foo(new <caret>)
      """);
    type("File");
    myFixture.assertPreferredCompletionItems(0, "File", "File");
    type("(");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("new File()"));
  }

  public void testNoAutopopupAfterDef() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    myFixture.configureByText("a.groovy", "def <caret>");
    type("a");
    assertNull(getLookup());
  }

  public void test_expand_class_list_when_typing_more_or_moving_caret() {
    myFixture.addClass("package foo; public class KimeFamilyRange {}");
    myFixture.addClass("package foo; public class FamiliesRangesMetaData {}");
    myFixture.addClass("public class KSomethingInCurrentPackage {}");
    myFixture.configureByText("a.groovy", "<caret>");

    type("F");
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("aRa");
    myFixture.assertPreferredCompletionItems(0, "FamiliesRangesMetaData", "KimeFamilyRange");

    for (int i = 0; i < 4; i++) {
      UsefulTestCase.edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
      myTester.joinCompletion();
    }
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("K");

    for (int i = 0; i < 4; i++) {
      UsefulTestCase.edt(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
      myTester.joinCompletion();
    }

    myFixture.assertPreferredCompletionItems(0, "KimeFamilyRange");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_1;
  }
}
