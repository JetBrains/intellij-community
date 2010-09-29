package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.08.2007
 */
public class AllSuperMethodsTest extends OverridingTester {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "allSuperMethods";
  }

  public void testExtendsAndImplements() throws Throwable { doTest(); }
  public void testExtendsClass() throws Throwable { doTest(); }
  public void testImplementsImplements() throws Throwable { doTest(); }
  public void testImplementsInterface() throws Throwable { doTest(); }
  public void testManyOverrMethods2() throws Throwable { doTest(); }
  public void testManySuperMethods() throws Throwable { doTest(); }
  public void testMethodWithoutParameters() throws Throwable { doTest(); }
  public void testMethodWithParameters() throws Throwable { doTest(); }

  @Override
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findSuperMethods();
  }
}
