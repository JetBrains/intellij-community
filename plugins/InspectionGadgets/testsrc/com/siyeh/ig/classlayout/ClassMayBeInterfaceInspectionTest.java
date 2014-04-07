/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ClassMayBeInterfaceInspectionTest extends LightInspectionTestCase {

  public void testOne() {
    doTest("abstract class /*Abstract class 'ConvertMe' may be interface*/ConvertMe/**/ {\n" +
           "    public static final String S = \"\";\n" +
           "    public void m() {}\n" +
           "    public static void n() {\n" +
           "        new ConvertMe() {};\n" +
           "        class X extends ConvertMe {}\n" +
           "    }\n" +
           "    public class A {}\n" +
           "}");
  }

  public void testOnTwo() {
    doTest("class ConvertMe {\n" +
           "    public static final String S = \"\";\n" +
           "    public void m() {}\n" +
           "    public static void n() {\n" +
           "        new ConvertMe() {};\n" +
           "        class X extends ConvertMe {}\n" +
           "    }\n" +
           "    public class A {}\n" +
           "}");
  }
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassMayBeInterfaceInspection();
  }
}
