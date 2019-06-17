// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("CastToIncompatibleInterface")
public class CastToIncompatibleInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class X { " +
           "  I list = (/*Cast to incompatible interface 'I'*/I/**/) new C(); " +
           "}" +
           "interface I {}" +
           "class C {}");
  }

  public void testHashMap() {
    doTest("import java.util.HashMap;" +
           "import java.util.List;" +
           "class X {" +
           "  List l = (/*Cast to incompatible interface 'List'*/List/**/) new HashMap();" +
           "}");
  }

  public void testCastToIncompatibleInterface() {
    doTest();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new CastToIncompatibleInterfaceInspection();
  }
}
