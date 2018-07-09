/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UseOfAnotherObjectsPrivateFieldInspectionTest extends LightInspectionTestCase {

  public void testUseOfAnotherObjectsPrivateField() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final UseOfAnotherObjectsPrivateFieldInspection inspection = new UseOfAnotherObjectsPrivateFieldInspection();
    inspection.ignoreSameClass = true;
    inspection.ignoreInnerClasses = true;
    return inspection;
  }
}
