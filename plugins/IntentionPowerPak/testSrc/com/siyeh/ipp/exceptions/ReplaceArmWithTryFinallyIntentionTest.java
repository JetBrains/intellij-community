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
 * @see ReplaceArmWithTryFinallyIntention
 */
public class ReplaceArmWithTryFinallyIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        /*_Replace 'try-with-resources' with 'try finally'*/try (Reader r = new StringReader()) {\n" +
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
      "        /*_Replace 'try-with-resources' with 'try finally'*/try (r1; Reader r2 = new StringReader()) {\n" +
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

  public void testArmWithFinally() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        /*_Replace 'try-with-resources' with 'try finally'*/try (Reader r = new StringReader()) {\n" +
      "            System.out.println(r);\n" +
      "        } finally {\n" +
      "          System.out.println();\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m() throws Exception {\n" +
      "        try {\n" +
      "            Reader r = new StringReader();\n" +
      "            try {\n" +
      "                System.out.println(r);\n" +
      "            } finally {\n" +
      "                r.close();\n" +
      "            }\n" +
      "        } finally {\n" +
      "          System.out.println();\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testMultipleResourcesWithCatch() {
    doTest(
      "import java.io.*;\n" +
      "class C {\n" +
      "    void m(StringReader r1, StringReader r2) {\n" +
      "        try/*_Replace 'try-with-resources' with 'try finally'*/ (r1; r2) {\n" +
      "            System.out.println(r1);\n" +
      "        } catch (RuntimeException e) {\n" +
      "            e.printStackTrace();\n" +
      "        }\n" +
      "    }\n" +
      "}\n",

      "import java.io.*;\n" +
      "class C {\n" +
      "    void m(StringReader r1, StringReader r2) {\n" +
      "        try {\n" +
      "            try {\n" +
      "                try {\n" +
      "                    System.out.println(r1);\n" +
      "                } finally {\n" +
      "                    r2.close();\n" +
      "                }\n" +
      "            } finally {\n" +
      "                r1.close();\n" +
      "            }\n" +
      "        } catch (RuntimeException e) {\n" +
      "            e.printStackTrace();\n" +
      "        }\n" +
      "    }\n" +
      "}\n"
    );
  }
}
