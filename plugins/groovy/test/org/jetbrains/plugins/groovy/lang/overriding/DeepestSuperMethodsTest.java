package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.08.2007
 */
public class DeepestSuperMethodsTest extends OverridingTester {

  public void testManyOverridingMethods() throws Throwable { doTest(); }
  public void testManyOverrMethods2() throws Throwable { doTest(); }
  public void testManyOverrMethods3() throws Throwable { doTest(); }
  public void testManyOverrMethods4() throws Throwable { doTest(); }
  public void testOverrideClassMethod() throws Throwable { doTest(); }
  public void testOverrideInterfaceMethod() throws Throwable { doTest(); }


  @Override
  protected String getBasePath() {
    return super.getBasePath() + "deepestSuperMethods";
  }

  @Override
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

}
