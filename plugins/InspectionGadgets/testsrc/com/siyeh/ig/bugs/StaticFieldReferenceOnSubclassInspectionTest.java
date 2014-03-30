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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class StaticFieldReferenceOnSubclassInspectionTest extends LightInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StaticFieldReferenceOnSubclassInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package a;" +
      "public class A {" +
      "  private static class AA {\n" +
      "    public static final int VALUE = 5;\n" +
      "  }" +
      "  public static class AB extends AA {\n" +
      "  }" +
      "  public static final String S = \"\";" +
      "}",
      "package a;" +
      "public class B extends A {}"
    };
  }

  public void testSimple() {
    doStatementTest("System.out.println(a.B./*Static field 'S' declared in class 'a.A' but referenced via subclass 'a.B'*/S/**/);");
  }

  public void testNoWarn() {
    doStatementTest("System.out.println(a.A.AB.VALUE);");
  }
}
