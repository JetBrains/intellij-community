package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ResolveTestCase;

public class AntResolveTest extends ResolveTestCase {

  public void testDefaultTarget() throws Exception {
    doTargetTest();
  }

  public void testSingleDependsTarget() throws Exception {
    doTargetTest();
  }

  public void testMultipleDependsTarget() throws Exception {
    doTargetTest();
  }

  public void testMultipleDependsTarget1() throws Exception {
    doTargetTest();
  }

  public void testMultipleDependsTarget2() throws Exception {
    doTargetTest();
  }

  public void testAntCall() throws Exception {
    doTargetTest();
  }

  public void testPropValueInAttribute() throws Exception {
    doPropertyTest();
  }

  public void testRefid() throws Exception {
    doPropertyTest();
  }

  private void doTargetTest() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    assertTrue(target instanceof AntTarget);
  }

  private void doPropertyTest() throws Exception {
    PsiReference ref = configure();
    PsiElement property = ref.resolve();
    assertTrue(property instanceof AntProperty);
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data/psi/resolve/";
  }

  private PsiReference configure() throws Exception {
    return configureByFile(getTestName(false) + ".ant");
  }

}
