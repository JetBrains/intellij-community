// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.configureByExactName;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyAutoPopupTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_1);
  private CompletionAutoPopupTester myTester;

  @BeforeEach
  public void setUp() {
    myTester = new CompletionAutoPopupTester(groovy.get());
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true, groovy.get().getTestRootDisposable());
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
  }

  @AfterEach
  public void tearDown() {
    // The base resets the stamp after each test, so that an invokeLater of the auto-popup handler cancels itself.
    Editor editor = groovy.get().getEditor();
    if (editor != null) {
      ((DocumentEx)editor.getDocument()).setModificationStamp(0);
    }
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER;
  }

  private void type(String s) {
    myTester.typeWithPauses(s);
  }

  private LookupImpl getLookup() {
    return (LookupImpl)groovy.get().getLookup();
  }

  @Test
  public void testGenerallyFocusLookup() {
    configureByExactName(groovy.get(), "a.groovy", """
      
              String foo(String xxxxxx) {
                return xx<caret>
              }
          \
      """);
    EdtTestUtil.runInEdtAndWait(() -> groovy.get().doHighlighting());
    type("x");
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testTopLevelFocus() {
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("p");
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testNoLookupFocusOnUnresolvedQualifier() {
    configureByExactName(groovy.get(), "a.groovy", """
      xxx.<caret>""");
    type("h");//hashCode
    assertNull(getLookup());
  }

  @Test
  public void testNoLookupFocusOnUntypedQualifier() {
    configureByExactName(groovy.get(), "a.groovy", """
      
            def foo(xxx) {
              xxx.<caret>
            }\
      """);
    type("h");
    assertNull(getLookup());
  }

  @Test
  public void testImpossibleClosureParameter() {
    configureByExactName(groovy.get(), "a.groovy", "String a; { a.<caret> }");
    type("h");
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testFieldTypeLowercase() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    configureByExactName(groovy.get(), "a.groovy", "class Foo { <caret> }");
    type("aioobe");
    assertEquals(groovy.get().getLookupElementStrings(), List.of(ArrayIndexOutOfBoundsException.class.getSimpleName()));
  }

  @Test
  public void testNoWordCompletionAutoPopup() {
    configureByExactName(groovy.get(), "a.groovy", "def foo = \"f<caret>\"");
    type("o");
    assertNull(getLookup());
  }

  @Test
  public void testClassesAndPackagesInUnqualifiedImports() {
    groovy.get().addClass("package Xxxxx; public class Xxxxxxxxx {}");
    configureByExactName(groovy.get(), "a.groovy", "package foo; import <caret>");
    type("Xxx");
    assertEquals(groovy.get().getLookupElementStrings(), List.of("Xxxxxxxxx", "Xxxxx"));
  }

  @Test
  public void testPopupAfterDotAfterPackage() {
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("import jav");
    assertNotNull(getLookup());
    type(".");
    assertNotNull(getLookup());
  }

  @Test
  public void testTypingFirstVarargDot() {
    groovy.get().addClass("class Foo { static class Bar {} }");
    configureByExactName(groovy.get(), "a.groovy", "void foo(Foo<caret>[] a) { }");
    type(".");
    assertNotNull(getLookup());
    type(".");
    groovy.get().checkResult("void foo(Foo..<caret>[] a) { }");
  }

  @Test
  public void testTypingFirstVarargDot2() {
    groovy.get().addClass("class Foo { static class Bar {} }");
    configureByExactName(groovy.get(), "a.groovy", "void foo(Foo<caret>) { }");
    type(".");
    assertNotNull(getLookup());
    type(".");
    groovy.get().checkResult("void foo(Foo..<caret>) { }");
  }

  @Test
  public void testDotDot() {
    configureByExactName(groovy.get(), "a.groovy", "2<caret>");
    type(".");
    assertNotNull(getLookup());
    assertTrue(getLookup().isFocused());
    type(".");
    assertNull(getLookup());

    groovy.get().checkResult("2..<caret>");
  }

  @Test
  public void testInsideClosure() {
    configureByExactName(groovy.get(), "a.groovy", "def cl = { foo(); <caret> }");
    type("h");
    assertNotNull(getLookup());
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testNonImportedClass() {
    groovy.get().addClass("package foo; public class Abcdefg {}");
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("Abcde ");
    groovy.get().checkResult("import foo.Abcdefg\n\nAbcdefg <caret>");
  }

  @Test
  public void test_two_non_imported_classes_when_space_does_not_select_first_autopopup_item() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    groovy.get().addClass("package foo; public class Abcdefg {}");
    groovy.get().addClass("package bar; public class Abcdefg {}");
    configureByExactName(groovy.get(), "a.groovy", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
    EdtTestUtil.runInEdtAndWait(() -> groovy.get().performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    type(" ");
    groovy.get().checkResult("""
                            import foo.Abcdefg
                            
                            class Foo extends Abcdefg <caret>""");
  }

  @Test
  public void testTwoNonImportedClasses() {
    groovy.get().addClass("package foo; public class Abcdefg {}");
    groovy.get().addClass("package bar; public class Abcdefg {}");
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("Abcde ");
    groovy.get().checkResult("""
                            import bar.Abcdefg
                            
                            Abcdefg <caret>""");
  }

  @Test
  public void testPrivate() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    configureByExactName(groovy.get(), "a.groovy", "class Foo { <caret> }");
    type("pri");
    assertEquals("private", groovy.get().getLookupElementStrings().get(0));
  }

  @Test
  public void testFieldTypeNonImported() {
    groovy.get().addClass("package foo; public class PrimaBalerina {}");
    configureByExactName(groovy.get(), "a.groovy", "class Foo { <caret> }");
    type("PrimaB");
    assertEquals(groovy.get().getLookupElementStrings(), List.of("PrimaBalerina"));
  }

  @Test
  public void testEnteringLabel() {
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("FIS:");
    assertEquals("FIS:", groovy.get().getEditor().getDocument().getText());
  }

  @Test
  public void testEnteringNamedArg() {
    configureByExactName(groovy.get(), "a.groovy", "foo(<caret>)");
    type("has:");
    groovy.get().checkResult("foo(has:<caret>)");
  }

  @Test
  public void testEnteringMapKey() {
    configureByExactName(groovy.get(), "a.groovy", "[<caret>]");
    type("has:");
    groovy.get().checkResult("[has:<caret>]");
  }

  @Test
  public void testPreferRightCasedVariant() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    configureByExactName(groovy.get(), "a.groovy", "<caret>");
    type("boo");
    groovy.get().assertPreferredCompletionItems(0, "boolean");
    type("\b\b\bBoo");
    groovy.get().assertPreferredCompletionItems(0, "Boolean");
  }

  @Test
  public void testPackageQualifier() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    groovy.get().addClass("package com.too; public class Util {}");
    configureByExactName(groovy.get(), "a.groovy", "void foo(Object command) { <caret> }");
    type("com.t");
    assertTrue(groovy.get().getLookupElementStrings().containsAll(List.of("too", "command.toString")));
  }

  @Test
  public void testVarargParenthesis() {
    configureByExactName(groovy.get(), "a.groovy", """
      
      void foo(File... files) { }
      foo(new <caret>)
      """);
    type("File");
    groovy.get().assertPreferredCompletionItems(0, "File", "File");
    type("(");
    assertTrue(groovy.get().getEditor().getDocument().getText().contains("new File()"));
  }

  @Test
  public void testNoAutopopupAfterDef() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    configureByExactName(groovy.get(), "a.groovy", "def <caret>");
    type("a");
    assertNull(getLookup());
  }

  @Test
  public void test_expand_class_list_when_typing_more_or_moving_caret() {
    groovy.get().addClass("package foo; public class KimeFamilyRange {}");
    groovy.get().addClass("package foo; public class FamiliesRangesMetaData {}");
    groovy.get().addClass("public class KSomethingInCurrentPackage {}");
    configureByExactName(groovy.get(), "a.groovy", "<caret>");

    type("F");
    assertFalse(groovy.get().getLookupElementStrings().contains("KimeFamilyRange"));

    type("aRa");
    groovy.get().assertPreferredCompletionItems(0, "FamiliesRangesMetaData", "KimeFamilyRange");

    for (int i = 0; i < 4; i++) {
      EdtTestUtil.runInEdtAndWait(() -> groovy.get().performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
      myTester.joinCompletion();
    }
    assertFalse(groovy.get().getLookupElementStrings().contains("KimeFamilyRange"));

    type("K");

    for (int i = 0; i < 4; i++) {
      EdtTestUtil.runInEdtAndWait(() -> groovy.get().performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
      myTester.joinCompletion();
    }

    groovy.get().assertPreferredCompletionItems(0, "KimeFamilyRange");
  }

}
