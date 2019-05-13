// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessarySuperConstructorInspectionTest extends LightInspectionTestCase {

  public void testQualifiedSuper() {
    doTest("class Outer {" +
           "  class Super {}" +
           "  class Inner extends Super {" +
           "    Inner(Outer outer) {" +
           "      outer.super();" +
           "    }" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("class Simple {" +
           "  Simple() {" +
           "    /*'super()' is unnecessary*/super()/**/;" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessarySuperConstructorInspection();
  }
}
