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

/**
 * @author Bas Leijdekkers
 */
public class SplitTryWithMultipleResourcesIntentionTest extends LightCodeInsightFixtureTestCase {
  public void testSimple() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        /*_*/try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "            System.out.println(in + \", \" + out);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        try (FileInputStream in = new FileInputStream(file1)) {\n" +
      "            try (FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "                System.out.println(in + \", \" + out);\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testWithCatch() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) {\n" +
      "        try (FileInputStream in = new FileInputStream(file1); /*_*/FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "            System.out.println(in + \", \" + out);\n" +
      "        } catch (IOException e) {\n" +
      "            e.printStackTrace();\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) {\n" +
      "        try (FileInputStream in = new FileInputStream(file1)) {\n" +
      "            try (FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "                System.out.println(in + \", \" + out);\n" +
      "            }\n" +
      "        } catch (IOException e) {\n" +
      "            e.printStackTrace();\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testMixedResources() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r2 = new StringReader();\n" +
      "        /*_*/try (Reader r1 = new StringReader(); r2) {\n" +
      "            System.out.println(r1 + \", \" + r2);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r2 = new StringReader();\n" +
      "        try (Reader r1 = new StringReader()) {\n" +
      "            try (r2) {\n" +
      "                System.out.println(r1 + \", \" + r2);\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  private void doTest(@Language("JAVA") String before, @Language("JAVA") String after) {
    myFixture.configureByText("a.java", before.replace("/*_*/", "<caret>"));
    myFixture.launchAction(myFixture.findSingleIntention(new SplitTryWithMultipleResourcesIntention().getText()));
    myFixture.checkResult(after);
  }
}
