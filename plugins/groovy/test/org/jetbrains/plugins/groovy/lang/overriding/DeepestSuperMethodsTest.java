package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;

public class DeepestSuperMethodsTest extends OverridingTester {

  public void testManyOverridingMethods() { doTest(); }
  public void testManyOverrMethods2() { doTest(); }
  public void testManyOverrMethods3() { doTest(); }
  public void testManyOverrMethods4() { doTest(); }
  public void testOverrideClassMethod() { doTest(); }
  public void testOverrideInterfaceMethod() { doTest(); }


  @Override
  protected String getBasePath() {
    return super.getBasePath() + "deepestSuperMethods";
  }

  @Override
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

}
