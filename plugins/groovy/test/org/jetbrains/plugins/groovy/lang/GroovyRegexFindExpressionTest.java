// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

public class GroovyRegexFindExpressionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);

    myFixture.configureByText("A.groovy", """
      class A {
        private static boolean xxx(boolean f) {
          return f;
        }
      
        public static void main(String[] args) {
          boolean f1 = "aaa" =~ /aaa/
          boolean f2 = ("aaa" =~ /aaa/)
          boolean f3 = ((("aaa" =~ /aaa/)))
      //    boolean f4
      //    f4 = ((("aaa" =~ /aaa/)))
      
          Boolean f5 = "aaa" =~ /aaa/, f6 = "aaa" =~ /bbb/
      
          assert "aaa" =~ /aaa/
          if ("aaa" =~ /aaa/ ? 1 : 2)
      
          // Erorrs
          int <warning descr="Cannot assign 'Matcher' to 'int'">x</warning> = "aaa" =~ /aaa/
          xxx<warning descr="'xxx' in 'A' cannot be applied to '(java.util.regex.Matcher)'">("aaa" =~ /aaa/)</warning>
        }
      }
      """);

    myFixture.checkHighlighting(true, false, true);
  }

  public void testRegex() {
    myFixture.configureByText("a.groovy", "'foo' =~ /\\s/");
    myFixture.checkHighlighting(true, false, true);
  }
}
