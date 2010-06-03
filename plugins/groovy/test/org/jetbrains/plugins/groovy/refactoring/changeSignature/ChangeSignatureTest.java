/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;

/**
 * @author Maxim.Medvedev
 */
public class ChangeSignatureTest extends ChangeSignatureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/changeSignature/";
  }

  public void testOneNewParameter() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo("p", -1, "\"5\"", null, CommonClassNames.JAVA_LANG_STRING)});
  }

  public void testRemoveParameter() throws Exception {
    doTest(new SimpleInfo[0]);
  }

  public void testInsertParameter() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", "-3", PsiType.INT),
      new SimpleInfo(1)
                                 });
  }

  public void testInsertOptionalParameter() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(1),
      new SimpleInfo("p", -1, "5", "-3", PsiType.INT)
                                 });
  }

  public void testNamedParametersRemove() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(2)
                                 });
  }

  public void testNamedParametersOrder1() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2)
                                 });
  }

  /*public void testNamedParametersOrder2() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
      new SimpleInfo(2),
                                 });
  }

  public void testNamedParametersOrder3() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
                                 });
  }*/

  public void testMoveNamedParameters() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(0)
                                 });
  }

  public void testMoveVarArgParameters() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(0)
                                 });
  }

  public void testChangeVisibilityAndName() throws Exception {
    doTest("protected", "newName", null, new SimpleInfo[]{new SimpleInfo(0)}, new ThrownExceptionInfo[0], false);
  }

  public void testImplicitConstructorInConstructor() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testImplicitConstructorForClass() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testAnonymousClassUsage() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testGroovyDocReferences() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2)
                                 });
  }

  public void testOverriders() throws Exception {
    doTest("public", "bar", null, new SimpleInfo[]{new SimpleInfo(0)}, new ThrownExceptionInfo[0], false);
  }

  public void testParameterRename() throws Exception {
    doTest(new SimpleInfo[]{new SimpleInfo("newP", 0)});
  }

  public void testAddReturnType() throws Exception {
    doTest("int", new SimpleInfo[]{new SimpleInfo(0)});
  }

  public void testChangeReturnType() throws Exception {
    doTest("int", new SimpleInfo[]{new SimpleInfo(0)});
  }

  public void testRemoveReturnType() throws Exception {
    doTest("", new SimpleInfo[]{new SimpleInfo(0)});
  }

  public void testChangeParameterType() throws Exception {
    doTest("", new SimpleInfo[]{new SimpleInfo("p", 0, null, null, PsiType.INT)});
  }

  public void testGenerateDelegate() throws Exception {
    doTest("", new SimpleInfo[]{new SimpleInfo(0), new SimpleInfo("p", -1, "2", "2", PsiType.INT)}, true);
  }

  public void testAddException() throws Exception {
    doTest("public", null, "",
           new SimpleInfo[]{new SimpleInfo(0)},
           new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))},
           false
          );
  }

  public void testExceptionCaughtInUsage() throws Exception {
    doTest("public", null, "",
           new SimpleInfo[]{new SimpleInfo(0)},
           new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))},
           false
          );
  }

  public void testExceptionInClosableBlock() throws Exception {
    doTest("public", null, "",
           new SimpleInfo[]{new SimpleInfo(0)},
           new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))},
           false
          );
  }

  public void testGenerateDelegateForConstructor() throws Exception {
    doTest("public", "Foo", null, new SimpleInfo[]{new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiType.INT)},
           new ThrownExceptionInfo[0], true);
  }

  public void testGenerateDelegateForAbstract() throws Exception {
    doTest("public", "foo", null, new SimpleInfo[]{new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiType.INT)},
           new ThrownExceptionInfo[0], true);
  }

  public void testTypeParameters() throws Exception {
    doTest(new SimpleInfo[]{new SimpleInfo("list", -1, "null", null, "java.util.List<T>"), new SimpleInfo(0)});
  }

  public void testEnumConstructor() throws Exception {
    doTest(new SimpleInfo[]{new SimpleInfo("a", -1, "2", null, PsiType.INT)});
  }

  public void testMoveArrayToTheEnd() throws Exception {
    doTest(new SimpleInfo[] {new SimpleInfo(1), new SimpleInfo(0)});
  }

  public void testReplaceVarargWithArray() throws Exception {
    doTest(new SimpleInfo[]{new SimpleInfo("l", 1, null, null, "List<T>[]"), new SimpleInfo(0)});
  }

  public void testReplaceVarargWithArray2() throws Exception {
    doTest(new SimpleInfo[]{new SimpleInfo("l", 1, null, null, "Map<T, E>[]"), new SimpleInfo(0)});
  }

  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(getProject()));
  }


  private void doTest(SimpleInfo[] parameterInfos) throws Exception {
    doTest("public", null, null, parameterInfos, new ThrownExceptionInfo[0], false);
  }

  private void doTest(String newReturnType, SimpleInfo[] parameterInfos) throws Exception {
    doTest("public", null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], false);
  }

  private void doTest(String newReturnType, SimpleInfo[] parameterInfos, final boolean generateDelegate) throws Exception {
    doTest("public", null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  private void doTest(String newVisibility,
                      String newName,
                      String newReturnType,
                      SimpleInfo[] parameterInfo,
                      ThrownExceptionInfo[] exceptionInfo,
                      final boolean generateDelegate) throws Exception {
    final File javaSrc = new File(getTestDataPath() + "/" + getTestName(false) + ".java");
    if (javaSrc.exists()) {
      myFixture.copyFileToProject(getTestName(false) + ".java");
    }
    myFixture.configureByFile(getTestName(false) + ".groovy");
    executeRefactoring(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo, getProject()),
                       new SimpleExceptionsGen(exceptionInfo), generateDelegate);
    if (javaSrc.exists()) {
      myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
    }
    myFixture.checkResultByFile(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true);
  }
}
