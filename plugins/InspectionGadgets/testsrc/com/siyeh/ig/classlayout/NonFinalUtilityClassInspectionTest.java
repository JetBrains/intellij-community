// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class NonFinalUtilityClassInspectionTest extends LightCodeInsightFixtureTestCase {
  private final DedicatedClassNameImplicitSubclassProvider myImplicitSubclassProvider =
    new DedicatedClassNameImplicitSubclassProvider("ConcreteNoUtilityClass");

  @Override
  protected String getBasePath() {
    return LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classlayout/non_final_utility_class";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil
      .registerExtension(ImplicitSubclassProvider.EP_NAME, myImplicitSubclassProvider, getTestRootDisposable());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new NonFinalUtilityClassInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNonFinalUtilityClass() {
    doTest();
  }

  private static class DedicatedClassNameImplicitSubclassProvider extends ImplicitSubclassProvider {
    private final String classNameToSubclass;

    DedicatedClassNameImplicitSubclassProvider(String classNameToSubclass) {
      this.classNameToSubclass = classNameToSubclass;
    }

    @Override
    public boolean isApplicableTo(@NotNull PsiClass psiClass) {
      return Objects.equals(psiClass.getName(), classNameToSubclass);
    }

    @Nullable
    @Override
    public SubclassingInfo getSubclassingInfo(@NotNull PsiClass psiClass) {
      return new SubclassingInfo(classNameToSubclass + " is subclassed at runtime");
    }
  }
}
