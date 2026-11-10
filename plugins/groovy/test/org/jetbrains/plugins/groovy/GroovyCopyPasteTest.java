// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.MagicConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyCopyPasteTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @MagicConstant(intValues = {CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK})
  private int myAddImportsOld;

  @BeforeEach
  void setUp() {

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    settings.ADD_IMPORTS_ON_PASTE = myAddImportsOld;
  }

  private void doTest(String fromText, String toText, String expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("fromFileName.groovy", fromText);
    myFixture.performEditorAction(IdeActions.ACTION_COPY);
    myFixture.configureByText("b.groovy", toText);
    myFixture.performEditorAction(IdeActions.ACTION_PASTE);
    myFixture.checkResult(expected);
  }

  @Test
  public void testRestoreImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Foo {}");

    doTest("""
             import foo.*; <selection>Foo f</selection>""", "<caret>", """
             import foo.Foo
             
             Foo f""");
  }

  @Test
  public void testGStringEolReplace() {
    doTest("""
             <selection>first
             second
             </selection>""", """
             def x = ""\"
             <selection>foo
             </selection>""\"""", """
             def x = ""\"
             first
             second
             <caret>""\"""");
  }

  @Test
  public void testPasteEnumConstant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package pack;\nenum E {\n  CONST\n}\n");
    doTest("""
             import static pack.E.CONST
             print <selection>CONST</selection>
             """, """
             print <caret>
             """, """
             import static pack.E.CONST
             
             print CONST<caret>
             """);
  }

  @Test
  public void testMultilinePasteIntoLineComment() {
    doTest("<selection>multiline\ntext</selection>", """
             class C {
                 //<caret>
             }""",
           """
             class C {
                 //multiline
                 //text<caret>
             }""");
  }
  
  @Test
  public void testStaticImport() {
    doTest("""
             import static java.lang.annotation.RetentionPolicy.CLASS
             
             <selection>System.out.println(CLASS)
             System.out.println(CLASS)
             System.out.println(CLASS)</selection>
             """,
           """
             <caret>
             """,
           """
             import static java.lang.annotation.RetentionPolicy.CLASS
             
             System.out.println(CLASS)
             System.out.println(CLASS)
             System.out.println(CLASS)
             """);
  }

  @Test
  public void testPasteFakeDiamond() {
    doTest("<selection>void foo(Map<> a) {}</selection>", """
      class A {
      
          <caret>
      }
      """, """
             class A {
             
                 void foo(Map<> a) {}
             }
             """);
  }
}
