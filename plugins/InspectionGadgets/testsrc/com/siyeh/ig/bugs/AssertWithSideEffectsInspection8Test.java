// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class AssertWithSideEffectsInspection8Test extends IGInspectionTestCase {
  @Override
  protected Sdk getTestProjectSdk() {
    // uses Regex
    return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk18());
  }

  public void test() {
    doTest("com/siyeh/igtest/bugs/assert_with_side_effects8",
           new AssertWithSideEffectsInspection());
  }
}