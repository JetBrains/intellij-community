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

import com.intellij.codeInsight.unwrap.UnwrapHandler
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class GroovyUnwrapTest extends LightJavaCodeInsightFixtureTestCase {

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    myFixture.configureByText("A.groovy", codeBefore)
    new UnwrapHandler().invoke(project, myFixture.editor, myFixture.file)
    myFixture.checkResult(expectedCodeAfter)
  }


  void testUnwrapIf() {
    assertUnwrapped("""
if (true) {
  a=1;
    c = 3
  b=1;<caret>
}
""",
"""a=1;
c = 3
b=1;""")
  }

  void testUnwrapFor1() {
    assertUnwrapped("""
for(int i = 0; i < 10; i++) {
    Sys<caret>tem.gc();
}
""", "Sys<caret>tem.gc();")
  }

  void testBraces() throws Exception {
    assertUnwrapped("""\
<caret>{
  def x = 1
}
""", "def x = 1")
  }

  void testUnwrapParameterUnderArgumentList() throws Exception {
    assertUnwrapped("xxx(1, yyy(<caret>1), 2)",
                    "xxx(1, <caret>1, 2)")
  }

  void testTryWithCatches() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} catch(RuntimeException e) {\n" +
                    "    int j;\n" +
                    "} catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}",

                    "int i;")
  }

  void testConditionalThat() throws Exception {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n",
                    "xxx('1');\n")
  }

  void testConditionalElse() throws Exception {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n",
                    "xxx('2' +<caret> 3);\n")
  }

  void testConditionalFromParameterList2() throws Exception {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n",
                    "xxx(11, '2' +<caret> 3, 12);\n")
  }

  void testConditionalCond1() throws Exception {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3",
                    "\"1\"")
  }

  void testConditionalCond2() throws Exception {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3",
                    "\"1\"")
  }

  void testConditionalUnwrapUnderAssigmentExpression() throws Exception {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n",
                    "String s = \"1\";\n")
  }

}
