/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ipp.concatenation;

import com.siyeh.ipp.IPPTestCase;
import junit.framework.TestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceFormatStringWithConcatenationIntentionTest extends IPPTestCase {

  public void testNotSupported() {
    doTestIntentionNotAvailable("class C {" +
                                "  String m() {" +
                                "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%d\", 1);" +
                                "  }" +
                                "}");
  }

  public void testSimple() {
    doTest("class C {" +
           "  String m() {" +
           "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%s\", 1);" +
           "  }" +
           "}",

           "class C {" +
           "  String m() {" +
           "    return String.valueOf(1);" +
           "  }" +
           "}");
  }

  public void testMultiple() {
    doTest("class C {" +
           "  String m(String expected, String actual) {" +
           "    return String./*_Replace 'String.format()' with concatenation*/format(\"Expected to get a '%s', got a '%s' instead\", expected, actual);" +
           "  }" +
           "}",

           "class C {" +
           "  String m(String expected, String actual) {" +
           "    return \"Expected to get a '\" + expected + \"', got a '\" + actual + \"' instead\";" +
           "  }" +
           "}");
  }

}