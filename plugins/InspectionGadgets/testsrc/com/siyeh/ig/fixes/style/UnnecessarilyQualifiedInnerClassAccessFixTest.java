// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessarilyQualifiedInnerClassAccessInspection;

public class UnnecessarilyQualifiedInnerClassAccessFixTest extends IGQuickFixesTestCase {

  public void testRemoveQualifier() {
    doTest("Remove qualifier",
      "class X {\n" +
      "  /**/X/*1*/./*2*/Y foo;\n" +
      "  \n" +
      "  class Y{}\n" +
      "}",

      "class X {\n" +
      "  /*1*//*2*/ Y foo;\n" +
      "  \n" +
      "  class Y{}\n" +
      "}"
    );
  }

  public void testRemoveQualifierWithImport() {
    doTest("Remove qualifier",
      "package p;\n" +
      "import java.util.List;\n" +
      "abstract class X implements List</**/X.Y> {\n" +
      "  class Y{}\n" +
      "}",

      "package p;\n" +
      "import p.X.Y;\n" +
      "\n" +
      "import java.util.List;\n" +
      "abstract class X implements List<Y> {\n" +
      "  class Y{}\n" +
      "}"
    );
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessarilyQualifiedInnerClassAccessInspection();
  }
}
