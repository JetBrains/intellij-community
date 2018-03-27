package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.siyeh.ig.IGInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonFinalUtilityClassInspectionTest extends IGInspectionTestCase {

  private DedicatedClassNameImplicitSubclassProvider myImplicitSubclassProvider =
    new DedicatedClassNameImplicitSubclassProvider("ConcreteNoUtilityClass");

  public void test() {
    doTest("com/siyeh/igtest/classlayout/non_final_utility_class", new NonFinalUtilityClassInspection());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil
      .registerExtension(ImplicitSubclassProvider.EP_NAME, myImplicitSubclassProvider, getTestRootDisposable());
  }

  private static class DedicatedClassNameImplicitSubclassProvider extends ImplicitSubclassProvider {
    private final String classNameToSubclass;

    public DedicatedClassNameImplicitSubclassProvider(String classNameToSubclass) {
      this.classNameToSubclass = classNameToSubclass;
    }

    @Override
    public boolean isApplicableTo(@NotNull PsiClass psiClass) {
      return psiClass.getName().equals(classNameToSubclass);
    }

    @Nullable
    @Override
    public SubclassingInfo getSubclassingInfo(@NotNull PsiClass psiClass) {
      return new SubclassingInfo(classNameToSubclass + " is subclassed at runtime");
    }
  }
}