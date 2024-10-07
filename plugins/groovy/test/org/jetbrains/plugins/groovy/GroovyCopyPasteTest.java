// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.MagicConstant;

public class GroovyCopyPasteTest extends LightJavaCodeInsightFixtureTestCase {
  @MagicConstant(intValues = {CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK})
  private int myAddImportsOld;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      settings.ADD_IMPORTS_ON_PASTE = myAddImportsOld;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(String fromText, String toText, String expected) {
    myFixture.configureByText("fromFileName.groovy", fromText);
    myFixture.performEditorAction(IdeActions.ACTION_COPY);
    myFixture.configureByText("b.groovy", toText);
    myFixture.performEditorAction(IdeActions.ACTION_PASTE);
    myFixture.checkResult(expected);
  }

  public void testRestoreImports() {
    myFixture.addClass("package foo; public class Foo {}");

    doTest("""
             import foo.*; <selection>Foo f</selection>""", "<caret>", """
             import foo.Foo
             
             Foo f""");
  }

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

  public void testPasteEnumConstant() {
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
