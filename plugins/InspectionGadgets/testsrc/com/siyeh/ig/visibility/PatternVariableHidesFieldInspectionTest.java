// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PatternVariableHidesFieldInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class Pointless {" +
           "    Integer p = new Integer(1);" +
           "    public void test(Object a) {" +
           "        if (a instanceof Integer /*Pattern variable 'p' hides field in class 'Pointless'*/p/**/) {" +
           "            System.out.print(\"a is an integer (\" + p + ')');" +
           "        } else if (a instanceof String s) {" +
           "            System.out.println(\" a is a string (\" + s + ')');" +
           "        }" +
           "    }" +
           "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new PatternVariableHidesFieldInspection();
  }
}