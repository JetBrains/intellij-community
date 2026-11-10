// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyReparseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "reparse/");
  }

  public void checkReparse(String text, String type) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", text);
    String psiDump = EdtTestUtil.runInEdtAndGet(() -> {
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      final String psiBefore = DebugUtil.psiToString(myFixture.getFile(), true);

      myFixture.type(type);
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      final String psiAfter = DebugUtil.psiToString(myFixture.getFile(), true);
      return psiBefore.trim() + "\n---\n" + psiAfter.trim();
    });

    myFixture.configureByText("a.txt", psiDump);
    myFixture.checkResultByFile(myFixture.getTestName(false) + ".txt");
  }

  @Test
  public void testCodeBlockReparse() {
    checkReparse("foo 'a', {<caret>}", "\n");
  }

  @Test
  public void testSwitchCaseIf() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "if ");
  }

  @Test
  public void testSwitchCaseDef() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "def ");
  }

  @Test
  public void testSwitchCaseFor() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "for ");
  }

  @Test
  public void testSwitchCaseWhile() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "while ");
  }

  @Test
  public void testSwitchCaseDo() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "doo ");
  }

  @Test
  public void testSwitchCaseSwitch() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                         <caret>return 2
                       }
                     }
                   """, "switch ");
  }

  @Test
  public void testSwitchCaseDot() {
    checkReparse("""
                 
                     def foo() {
                       switch(x) {
                         case 2:
                           return <caret>
                         case 3:
                           return false
                         case 4:
                           return false
                       }
                     }
                   """, "foo.");
  }

  @Test
  public void testOpeningParenthesisAtBlockStart() {
    checkReparse("""
                 
                   def foo() {
                       <caret>String home
                       simplePlugins.each {
                           layoutPlugin it
                       }
                 
                   }
                   }""", "(");
  }

  @Test
  public void testNoVariableName() {
    checkReparse("""
                 
                   def foo() {
                       switch (w) {
                         case 2:
                           def x<caret> = xxx
                           if () {
                           }
                           return 2
                 
                       }
                       return state
                     }
                   """, "\b");
  }

  @Test
  public void testSwitchRParen() {
    checkReparse("""
                 
                   def foo() {
                       switch (word w w)<caret> {
                         case 2:
                           def x = (y)
                       }
                     }
                   """, "\b");
  }

  @Test
  public void testWhileRParen() {
    checkReparse("""
                 
                   def foo() {
                     def cl = {
                       while (true<caret> {
                         //
                       }
                     }
                   }""", ";");
  }

  @Test
  public void testSynchronizedRParen() {
    checkReparse("""
                 
                   def foo() {
                     def cl = {
                       synchronized (x<caret> {
                         //
                       }
                     }
                   }""", ";");
  }

  @Test
  public void testMultilineToNormalString() {
    checkReparse("""
                 
                   class a {
                     def foo() {
                       bar(""<caret>aaa")
                     }
                 
                     def bar() {
                       zoo()
                     }
                   }
                   """, "\b");
  }

  @Test
  public void testNewLinesBetweenBlocks() {
    checkReparse("""
                   class A {
                 
                     void foo() {
                       id0(id1{id2
                     }
                 
                     <selection> {
                       id3(}</selection>
                     }
                 
                     void bar() {}
                   }
                   """, "\b");
  }
}
