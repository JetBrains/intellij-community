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
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("SuspiciousToArrayCall")
public class SuspiciousToArrayCallInspectionTest extends LightJavaInspectionTestCase {

  public void testCast() {
    doMemberTest("public void testThis(java.util.List l) {" +
                 "  final String[][] ss = (String[][]) l.toArray(/*Array of type 'java.lang.String[][]' expected, 'java.lang.Number[]' found*/new Number[l.size()]/**/);" +
                 "}");
  }

  public void testParameterized() {
    doMemberTest("public void testThis(java.util.List<String> l) {" +
                 "  l.toArray(/*Array of type 'java.lang.String[]' expected, 'java.lang.Number[]' found*/new Number[l.size()]/**/);" +
                 "}");
  }

  public void testGenerics() {
    doTest("import java.util.*;" +
           "class K<T extends Integer> {\n" +
           "    List<T> list = new ArrayList<>();\n" +
           "\n" +
           "    String[] m() {\n" +
           "        return list.toArray(/*Array of type 'java.lang.Integer[]' expected, 'java.lang.String[]' found*/new String[list.size()]/**/);\n" +
           "    }\n" +
           "}");
  }

  public void testQuestionMark() {
    doTest("import java.util.List;\n" +
           "\n" +
           "class Test {\n" +
           "  Integer[] test(List<?> list) {\n" +
           "    return list.toArray(/*Array of type 'java.lang.Object[]' expected, 'java.lang.Integer[]' found*/new Integer[0]/**/);\n" +
           "  }\n" +
           "}");
  }

  public void testWrongGeneric() {
    doTest("import java.util.*;\n" +
           "\n" +
           "class Test {\n" +
           "  static class X<T> extends ArrayList<Integer> {}\n" +
           "  Integer[] test(X<Double> x) {\n" +
           "    return x.toArray(new Integer[0]);\n" +
           "  }\n" +
           "\n" +
           "  Double[] test2(X<Double> x) {\n" +
           "    return x.toArray(/*Array of type 'java.lang.Integer[]' expected, 'java.lang.Double[]' found*/new Double[0]/**/);\n" +
           "  }\n" +
           "}");
  }
  
  public void testStreams() {
    doTest("import java.util.stream.Stream;\n" +
           "class Test {\n" +
           "    static {\n" +
           "        Stream.of(1.0, 2.0, 3.0).toArray(/*Array of type 'java.lang.Double[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/);\n" +
           "    }\n" +
           "}");
  }
  
  public void testStreamsParens() {
    doTest("import java.util.stream.Stream;\n" +
           "class Test {\n" +
           "    static {\n" +
           "        Stream.of(1.0, 2.0, 3.0).toArray(((/*Array of type 'java.lang.Double[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/)));\n" +
           "    }\n" +
           "}");
  }
  
  public void testStreamsIntersection() {
    doTest("import java.util.stream.Stream;\n" +
           "class Test {\n" +
           "    static {\n" +
           "        Stream.of(1, 2.0, 3.0).toArray(/*Array of type 'java.lang.Number[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/);\n" +
           "    }\n" +
           "}");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousToArrayCallInspection();
  }
}
