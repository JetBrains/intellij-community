// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.openapi.application.PluginPathManager;
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
      "  /*2*//*1*/ Y foo;\n" +
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

  public void testUnnecessarilyQualifiedInnerClassAccess() {
    doTest("Fix all 'Unnecessarily qualified inner class access' problems in file");
  }

  public void testNoImports() {
    final UnnecessarilyQualifiedInnerClassAccessInspection inspection = new UnnecessarilyQualifiedInnerClassAccessInspection();
    inspection.ignoreReferencesNeedingImport = true;
    myFixture.enableInspections(inspection);
    doTest("Fix all 'Unnecessarily qualified inner class access' problems in file");
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test/com/siyeh/igtest/";
  }

  @Override
  protected String getRelativePath() {
    return "style/unnecessarily_qualified_inner_class_access";
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessarilyQualifiedInnerClassAccessInspection();
  }
}
