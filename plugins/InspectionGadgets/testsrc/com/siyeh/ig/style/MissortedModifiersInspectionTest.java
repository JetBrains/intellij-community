// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MissortedModifiersInspectionTest extends LightJavaInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  public void testMissortedModifiers() {
    doTest();
  }

  public void testIgnoreAnnotations() {
    final MissortedModifiersInspection inspection = new MissortedModifiersInspection();
    inspection.m_requireAnnotationsFirst = false;
    myFixture.enableInspections(inspection);
    doTest();
  }

  public void testTypeUseWithType() {
    final MissortedModifiersInspection inspection = new MissortedModifiersInspection();
    inspection.typeUseWithType = true;
    myFixture.enableInspections(inspection);
    doTestQuickFix();
  }

  public void testSimpleComment() {
    doTestQuickFix();
  }

  public void testAnotherComment() {
    doTestQuickFix();
  }

  public void testKeepAnnotationOrder() {
    doTestQuickFix();
  }

  public void testMissortedInModuleRequires(){
    doTestQuickFix();
  }

  public void testSealedClass() {
    doTestQuickFix();
  }

  public void doTestQuickFix() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MissortedModifiersInspection();
  }
}
