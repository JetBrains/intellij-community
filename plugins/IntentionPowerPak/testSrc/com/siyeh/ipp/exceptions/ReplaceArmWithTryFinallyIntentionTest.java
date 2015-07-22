/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ipp.exceptions;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

public class ReplaceArmWithTryFinallyIntentionTest extends LightCodeInsightFixtureTestCase {
  public void testSimple() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        /*_*/try (Reader r = new StringReader()) {\n" +
      "            System.out.println(r);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r = new StringReader();\n" +
      "        try {\n" +
      "            System.out.println(r);\n" +
      "        } finally {\n" +
      "            r.close();\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testMixedResources() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r1 = new StringReader();\n" +
      "        /*_*/try (r1; Reader r2 = new StringReader()) {\n" +
      "            System.out.println(r1 + \", \" + r2);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r1 = new StringReader();\n" +
      "        try {\n" +
      "            Reader r2 = new StringReader();\n" +
      "            try {\n" +
      "                System.out.println(r1 + \", \" + r2);\n" +
      "            } finally {\n" +
      "                r2.close();\n" +
      "            }\n" +
      "        } finally {\n" +
      "            r1.close();\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  private void doTest(@Language("JAVA") String before, @Language("JAVA") String after) {
    myFixture.configureByText("a.java", before.replace("/*_*/", "<caret>"));
    myFixture.launchAction(myFixture.findSingleIntention(new ReplaceArmWithTryFinallyIntention().getText()));
    myFixture.checkResult(after);
  }
}
