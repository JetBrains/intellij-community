/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant;

import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
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
    PsiReference ref = configure();
    PsiElement elem = ref.resolve();
    assertTrue(elem == null);
  }

  public void testFailProperty1() throws Exception {
    PsiReference ref = configure();
    PsiElement elem = ref.resolve();
    assertTrue(elem == null);
  }

  public void testEnvPropertyW() throws Exception {
    if (SystemInfo.isWindows) doPropertyTest();
  }

  public void testEnvPropertyU() throws Exception {
    if (SystemInfo.isUnix) doPropertyTest();
  }

  public void testEnvProperty1W() throws Exception {
    if (SystemInfo.isWindows) doPropertyTest();
  }

  public void testEnvProperty1U() throws Exception {
    if (SystemInfo.isUnix) doPropertyTest();
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

  public void testPropInDependieTarget1() throws Exception {
    doPropertyTest();
  }

  public void testPropDefinedInIfTargetAttribute() throws Exception {
    PsiReference ref = configure();
    PsiElement property = ref.resolve();
    assertTrue(property == null);
  }

  public void testPropDefinedInUnlessTargetAttribute() throws Exception {
    PsiReference ref = configure();
    PsiElement property = ref.resolve();
    assertTrue(property == null);
  }

  public void testBuildNumber() throws Exception {
    doPropertyTest();
  }
  public void testPropertyInMacrodefParam() throws Exception {
    PsiReference ref = configure();
    assertNotNull(ref.resolve());
  }

  public void testMacrodefElement() throws Exception {
    PsiReference ref = configure();
    assertNotNull(ref.resolve());
  }

  /* todo: rewrite into DOM
  public void testAntFilePropertyWithContexts() throws Exception {
    final AntPropertyReference refImporting = (AntPropertyReference)configureByFile("PropertyAntFileImporting.ant");
    final AntFile importing = refImporting.getElement().getAntFile();

    final VirtualFile vFile = importing.getVirtualFile();
    assertTrue(vFile != null);
    
    final AntPropertyReference refImported = (AntPropertyReference)configureByFile("PropertyAntFileImported.ant", vFile.getParent());
    final AntFile imported = refImported.getElement().getAntFile();

    AntConfigurationBase.getInstance(getProject()).setContextFile(imported, importing);
    importing.clearCaches(); // need this because imported file was created after the importing
                                    
    assertTrue(refImported.resolve() != null);
    assertTrue(refImporting.resolve() != null);
  }
  */
  
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
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/resolve/";
  }

  private PsiReference configure() throws Exception {
    return configureByFile(getTestName(false) + ".ant");
  }

}
