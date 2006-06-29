package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ResolveTestCase;
import junit.framework.AssertionFailedError;

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

  public void testPropValueInAttribute1() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute2() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute3() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute4() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute5() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute6() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute7() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute8() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttribute9() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeA() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeB() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeC() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeD() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeE() throws Exception {
    doPropertyTest();
  }

  public void testPropValueInAttributeF() throws Exception {
    doPropertyTest();
  }

  public void testExecProperty() throws Exception {
    doPropertyTest();
  }

  public void testExecProperty1() throws Exception {
    doPropertyTest();
  }

  public void testFailProperty() throws Exception {
    doPropertyTest();
  }

  public void testFailProperty1() throws Exception {
    doPropertyTest();
  }

  public void testNonExistingEnvProperty() throws Exception {
    boolean isNull = false;
    try {
      configure();
    }
    catch (AssertionFailedError e) {
      isNull = true;
    }
    assertTrue(isNull);
  }

  public void testNonExistingEnvProperty1() throws Exception {
    boolean isNull = false;
    try {
      configure();
    }
    catch (AssertionFailedError e) {
      isNull = true;
    }
    assertTrue(isNull);
  }

  public void testRefid() throws Exception {
    PsiReference ref = configure();
    assertNotNull(ref.resolve());
  }

  public void testIndirectRefid() throws Exception {
    PsiReference ref = configure();
    assertNotNull(ref.resolve());
  }

  public void testPredefinedProperty() throws Exception {
    doPropertyTest();
  }

  public void testMacroDef() throws Exception {
    doTaskTest();
  }

  public void testNestedMacroDef() throws Exception {
    doTaskTest();
  }

  public void testPresetDef() throws Exception {
    PsiReference ref = configure();
    assertNotNull(ref.resolve());
  }

  public void testPropInDependieTarget() throws Exception {
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

  private void doTaskTest() throws Exception {
    PsiReference ref = configure();
    PsiElement property = ref.resolve();
    assertTrue(property instanceof AntTask);
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data/psi/resolve/";
  }

  private PsiReference configure() throws Exception {
    return configureByFile(getTestName(false) + ".ant");
  }

}
