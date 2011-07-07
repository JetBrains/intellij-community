package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

/**
 * @author Sergey Evdokimov
 */
class GroovyRegexFindExpressionTest extends LightCodeInsightFixtureTestCase {

  public void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection)

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
    int x = <warning descr="Cannot assign 'Matcher' to 'int'">"aaa" =~ /aaa/</warning>
    xxx<warning descr="'xxx' in 'A' cannot be applied to '(java.util.regex.Matcher)'">("aaa" =~ /aaa/)</warning>
  }
}
""")

    myFixture.checkHighlighting(true, false, true)
  }
}
