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
package com.siyeh.ipp.trivialif;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeElseIfIntentionTest extends IPPTestCase {

  public void testCommentsAreKept() {
    doTest("class X {\n" +
           "    void test(boolean foo, boolean bar) {\n" +
           "        if (foo) { //asdf\n" +
           "\n" +
           "        } else/*_Merge 'else if'*/ {\n" +
           "            // blubb\n" +
           "            // blabb\n" +
           "            if (bar) { // asdf3\n" +
           "\n" +
           "            } // other\n" +
           "            // bla\n" +
           "        } // asdf1\n" +
           "        // asdf4\n" +
           "    }\n" +
           "}\n",

           "class X {\n" +
           "    void test(boolean foo, boolean bar) {\n" +
           "        if (foo) { //asdf\n" +
           "\n" +
           "        } else\n" +
           "            // blubb\n" +
           "            // blabb\n" +
           "            if (bar) { // asdf3\n" +
           "\n" +
           "            } // other\n" +
           "// bla\n" +
           "// asdf1\n" +
           "        // asdf4\n" +
           "    }\n" +
           "}\n");
  }
}
