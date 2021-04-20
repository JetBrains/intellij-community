// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ComparableImplementedButEqualsNotOverriddenInspectionTest extends LightJavaInspectionTestCase {

  public void testInterfaceImplementingComparable() { doTest(); }
  public void testSimple() { doTest(); }
  public void testAbstractClass1() { doTest(); }
  public void testAbstractClass2() { doTest(); }
  public void testAbstractClass3() { doTest(); }
  public void testNote() { doTest(); }
  public void testNoWarningForRecord() { doTest(); }

  public void testNoFixForAnonymousClass() {
    doTest();
    assertNotNull(myFixture.findSingleIntention(ComparableImplementedButEqualsNotOverriddenInspection.getGenerateEqualsFixName()));
    assertEmpty(myFixture.filterAvailableIntentions(ComparableImplementedButEqualsNotOverriddenInspection.getAddNoteFixName()));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ComparableImplementedButEqualsNotOverriddenInspection();
  }
}