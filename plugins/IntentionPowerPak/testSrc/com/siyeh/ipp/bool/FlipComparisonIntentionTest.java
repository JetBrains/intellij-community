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
package com.siyeh.ipp.bool;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FlipComparisonIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest("class X {" +
           "  boolean b = 1//some comment\n" +
           " >/*_Flip '>' to '<'*///another comment\n" +
           " 2;" +
           "}",

           "class X {  //some comment\n" +
           "    //another comment\n" +
           "    boolean b = 2 < 1;}");
  }

  public void testBrokenCode() {
    doTestIntentionNotAvailable("import java.util.*;" +
                                "class X {" +
                                "  String x(Set<String> set) {" +
                                "  }" +
                                "  {x(HashSet</*_Flip '>' to '<'*/>());}" +
                                "}");
  }
}