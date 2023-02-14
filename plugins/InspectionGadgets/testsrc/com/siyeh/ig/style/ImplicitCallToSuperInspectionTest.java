// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ImplicitCallToSuperInspectionTest extends LightJavaInspectionTestCase {

  public void testImplicitCallToSuper() { doTest(); }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ImplicitCallToSuperInspection();
  }
}