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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousToArrayCallInspectionTest extends LightInspectionTestCase {

  public void testCast() {
    doMemberTest("public void testThis(java.util.List l) {" +
                 "  final String[][] ss = (String[][]) l.toArray(/*Array of type 'java.lang.String[][]' expected*/new Number[l.size()]/**/);" +
                 "}");
  }

  public void testParameterized() {
    doMemberTest("public void testThis(java.util.List<String> l) {" +
                 "  l.toArray(/*Array of type 'java.lang.String[]' expected*/new Number[l.size()]/**/);" +
                 "}");
  }

  public void testGenerics() {
    doTest("import java.util.*;" +
           "class K<T extends Integer> {\n" +
           "    List<T> list = new ArrayList<>();\n" +
           "\n" +
           "    String[] m() {\n" +
           "        return list.toArray(/*Array of type 'java.lang.Integer[]' expected*/new String[list.size()]/**/);\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousToArrayCallInspection();
  }
}
