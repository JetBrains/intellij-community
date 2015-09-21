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

import com.siyeh.ipp.IPPTestCase;

/**
 * @see SplitTryWithMultipleResourcesIntention
 * @author Bas Leijdekkers
 */
public class SplitTryWithMultipleResourcesIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        /*_Split 'try' statement with multiple resources*/try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {\n" +
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
      "        try (FileInputStream in = new FileInputStream(file1); /*_Split 'try' statement with multiple resources*/FileOutputStream out = new FileOutputStream(file2)) {\n" +
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

  public void testWithFinally() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        /*_Split 'try' statement with multiple resources*/try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "            System.out.println(in + \", \" + out);\n" +
      "        } finally {\n" +
      "            System.out.println();\n" +
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
      "        } finally {\n" +
      "            System.out.println();\n" +
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
      "        /*_Split 'try' statement with multiple resources*/try (Reader r1 = new StringReader(); r2) {\n" +
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
}
