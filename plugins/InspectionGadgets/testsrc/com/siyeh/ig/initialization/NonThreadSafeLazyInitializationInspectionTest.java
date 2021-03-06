// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NonThreadSafeLazyInitializationInspectionTest extends LightJavaInspectionTestCase {

  public void testNonThreadSafeLazyInitialization() {
    doTest();
    checkQuickFixAll();
  }

  public void testNestedAssignment() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testLocalVariableReferenced() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testInstanceVariableReferenced() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testQualifiedInstanceMethod() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testStaticVariableReferenced() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testNormal() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NonThreadSafeLazyInitializationInspection();
  }
}
