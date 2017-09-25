package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;

public class AllSuperMethodsTest extends OverridingTester {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "allSuperMethods";
  }

  public void testExtendsAndImplements() { doTest(); }
  public void testExtendsClass() { doTest(); }
  public void testImplementsImplements() { doTest(); }
  public void testImplementsInterface() { doTest(); }
  public void testManyOverrMethods2() { doTest(); }
  public void testManySuperMethods() { doTest(); }
  public void testMethodWithoutParameters() { doTest(); }
  public void testMethodWithParameters() { doTest(); }

  @Override
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findSuperMethods();
  }
}
