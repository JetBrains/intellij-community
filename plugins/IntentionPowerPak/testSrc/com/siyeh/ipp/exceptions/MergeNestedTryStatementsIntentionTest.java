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
 * @see MergeNestedTryStatementsIntention
 * @author Bas Leijdekkers
 */
public class MergeNestedTryStatementsIntentionTest extends IPPTestCase {
  public void testSimple() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        /*_Merge nested 'try' statements*/try (FileInputStream in = new FileInputStream(file1)) {\n" +
      "            try (FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "                System.out.println(in + \", \" + out);\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1, File file2) throws IOException {\n" +
      "        try (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {\n" +
      "            System.out.println(in + \", \" + out);\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testWithoutAndWithResources() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file) {\n" +
      "        /*_Merge nested 'try' statements*/try {\n" +
      "            try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), \"utf-8\")) {\n" +
      "              System.out.println(r);\n" +
      "            }\n" +
      "        } catch (IOException e) {\n" +
      "            throw new RuntimeException(e);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file) {\n" +
      "        try (InputStreamReader r = new InputStreamReader(new FileInputStream(file), \"utf-8\")) {\n" +
      "            System.out.println(r);\n" +
      "        } catch (IOException e) {\n" +
      "            throw new RuntimeException(e);\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public void testOldStyle() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1) {\n" +
      "        /*_Merge nested 'try' statements*/try {\n" +
      "            try {\n" +
      "                FileInputStream in = new FileInputStream(file1);\n" +
      "            } catch (FileNotFoundException e) {\n" +
      "                // log\n" +
      "            }\n" +
      "        } catch (Exception e) {\n" +
      "            // log\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void foo(File file1) {\n" +
      "        try {\n" +
      "            FileInputStream in = new FileInputStream(file1);\n" +
      "        } catch (FileNotFoundException e) {\n" +
      "            // log\n" +
      "        } catch (Exception e) {\n" +
      "            // log\n" +
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
      "        /*_Merge nested 'try' statements*/try (r1) {\n" +
      "            try (Reader r2 = new StringReader()) {\n" +
      "                System.out.println(r1 + \", \" + r2);\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        Reader r1 = new StringReader();\n" +
      "        try (r1; Reader r2 = new StringReader()) {\n" +
      "            System.out.println(r1 + \", \" + r2);\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }
}
