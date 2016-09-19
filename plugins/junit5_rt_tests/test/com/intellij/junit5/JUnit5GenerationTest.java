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
package com.intellij.junit5;

import com.intellij.testIntegration.BaseGenerateTestSupportMethodAction;
import com.intellij.testIntegration.TestIntegrationUtils;
import org.junit.jupiter.api.Test;

class JUnit5GenerationTest extends JUnit5CodeInsightTest {
  @Test
  void testMethodInTopLevelClass() {
    doTest("import org.junit.jupiter.api.Test; class MyTest {<caret> @Test void m2(){}}",
           "import org.junit.jupiter.api.Test; class MyTest {\n" +
                                   "    @Test\n" +
                                   "    void name() {\n" +
                                   "        \n" +
                                   "\n" +
                                   "    }\n" +
                                   "\n" +
                                   "    @Test void m2(){}}");
  }

  @Test
  void testMethodInNestedClass() {
    doTest("import org.junit.jupiter.api.Nested; class MyTest { @Nested class NTest { <caret>}}",
           "import org.junit.jupiter.api.Nested;\n" +
           "import org.junit.jupiter.api.Test;\n" +
           "\n" +
           "class MyTest { @Nested class NTest {\n" +
           "    @Test\n" +
           "    void name() {\n" +
           "        \n" +
           "\n" +
           "    }\n" +
           "}}");
  }

  private void doTest(String text, String expected) {
    doTest(() -> {
             myFixture.configureByText("MyTest.java", text);

             new BaseGenerateTestSupportMethodAction.MyHandler(TestIntegrationUtils.MethodKind.TEST).invoke(myFixture.getProject(),
                                                                                                            myFixture.getEditor(),
                                                                                                            myFixture.getFile());
             myFixture.checkResult(expected);
           }
    );
  }
}
