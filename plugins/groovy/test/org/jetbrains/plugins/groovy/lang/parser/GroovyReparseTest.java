// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyReparseTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "reparse/";
  }

  public void checkReparse(String text, String type) {
    myFixture.configureByText("a.groovy", text);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    final String psiBefore = DebugUtil.psiToString(myFixture.getFile(), true);

    myFixture.type(type);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    final String psiAfter = DebugUtil.psiToString(myFixture.getFile(), true);

    myFixture.configureByText("a.txt", psiBefore.trim() + "\n---\n" + psiAfter.trim());
    myFixture.checkResultByFile(getTestName(false) + ".txt");
  }

  public void testCodeBlockReparse() {
    checkReparse("foo 'a', {<caret>}", "\n");
  }

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
