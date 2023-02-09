/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

class GroovyRegexFindExpressionTest extends LightJavaCodeInsightFixtureTestCase {

  void testHighlighting() {
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
    int <warning descr="Cannot assign 'Matcher' to 'int'">x</warning> = "aaa" =~ /aaa/
    xxx<warning descr="'xxx' in 'A' cannot be applied to '(java.util.regex.Matcher)'">("aaa" =~ /aaa/)</warning>
  }
}
""")

    myFixture.checkHighlighting(true, false, true)
  }

  void testRegex() {
    myFixture.configureByText('a.groovy', '\'foo\' =~ /\\s/')
    myFixture.checkHighlighting(true, false, true)
  }

}
