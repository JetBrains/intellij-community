// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.abstraction;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.abstraction.TypeMayBeWeakenedInspection;

import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
public class TypeMayBeWeakenedFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.setLanguageLevel(LanguageLevel.JDK_10);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TypeMayBeWeakenedInspection inspection = new TypeMayBeWeakenedInspection();
    inspection.onlyWeakentoInterface = false;
    inspection.doNotWeakenReturnType = false;
    inspection.doNotWeakenInferredVariableType = false;
    inspection.myStopClassSet = new OrderedSet<>(Collections.singletonList("com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
    myFixture.enableInspections(inspection);
    myRelativePath = "abstraction/type_may_be_weakened";
    ImplicitUsageProvider.EP_NAME.getExtensionList();
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  public void testShorten() { doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "java.util.Collection")); }
  public void testLocalClass() { doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "A")); }

  public void testGeneric() {
    doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.C"));
  }

  public void testStopClass() {
    doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
  }

  public void testJava10() {
    doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.B"));
  }
}
