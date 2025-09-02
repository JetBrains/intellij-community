// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.pattern;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PatternValidatorTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    var inspection = new PatternValidator();
    inspection.CHECK_NON_CONSTANT_VALUES = true;
    myFixture.enableInspections(inspection);
  }

  public void testPatternValidator() {
    highlightTest("""
      import org.intellij.lang.annotations.Pattern;

      class X {
        @Pattern("[0-9]+") String str = <warning descr="Expression '123a' doesn't match pattern: [0-9]+">"123a"</warning>;
       
        @Anno(foo = <warning descr="Expression '123b' doesn't match pattern: [0-9]+">"123b"</warning>)
        int x;
       
        @interface Anno {
          @Pattern("[0-9]+") String foo();
        }
      }""");
  }

  public void testAddLocalVariableFix() {
    quickFixTest("""
    import org.intellij.lang.annotations.Pattern;
      
    class X {
      @Pattern("[0-9]+")
      public static String createValue(int i) {
          return 123 + String.value<caret>Of(i);
      }
    }
    """, """
    import org.intellij.lang.annotations.Pattern;
    
    class X {
      @Pattern("[0-9]+")
      public static String createValue(int i) {
          String s = String.valueOf(i);
          return 123 + s;
      }
    }
    """, "Introduce variable");
  }

  public void highlightTest(String text) {
    myFixture.configureByText("X.java", text);
    myFixture.testHighlighting();
  }

  public void quickFixTest(String before, String after, String hint) {
    myFixture.configureByText("X.java", before);
    IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResult(after);
  }
}
