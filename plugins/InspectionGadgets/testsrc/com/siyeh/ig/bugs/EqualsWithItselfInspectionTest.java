// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author Bas Leijdekkers
 */
public class EqualsWithItselfInspectionTest extends LightJavaInspectionTestCase {

  public void testEqualsWithItself() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EqualsWithItselfInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}