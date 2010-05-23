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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
public class ChangeSignatureTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/changeSignature/";
  }

  public void testOneNewParameter() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo("p", -1, "\"5\"", null, createType(CommonClassNames.JAVA_LANG_STRING))});
  }

  public void testRemoveParameter() throws Exception {
    doTest(null, new SimpleInfo[0]);
  }

  public void testInsertParameter() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", "-3", PsiType.INT),
      new SimpleInfo(1)
                                 });
  }

  public void testInsertOptionalParameter() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(1),
      new SimpleInfo("p", -1, "5", "-3", PsiType.INT)
                                 });
  }

  public void testNamedParametersRemove() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(2)
                                 });
  }

  public void testNamedParametersOrder1() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2)
                                 });
  }

  public void testNamedParametersOrder2() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
      new SimpleInfo(2),
                                 });
  }

  public void testNamedParametersOrder3() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
                                 });
  }

  public void testMoveNamedParameters() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(0)
                                 });
  }

  public void testMoveVarArgParameters() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(0)
                                 });
  }

  public void testChangeVisibilityAndName() throws Exception {
    doTest("protected", "newName", null, new SimpleInfo[]{new SimpleInfo(0)}, new ThrownExceptionInfo[0], false);
  }

  public void testImplicitConstructorInConstructor() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testImplicitConstructorForClass() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testAnonymousClassUsage() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo("p", -1, "5", null, PsiType.INT)
                                 });
  }

  public void testGroovyDocReferences() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2)
                                 });
  }

  public void testOverriders() throws Exception {
    doTest("public", "bar", null, new SimpleInfo[]{
      new SimpleInfo(0)
                                                  }, new ThrownExceptionInfo[0], false);
  }

  public void testParameterRename() throws Exception {
    doTest(null, new SimpleInfo[]{
      new SimpleInfo("newP", 0)
                                 });
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

  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(getProject()));
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
    doTestForGroovy(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo), new SimpleExceptionsGen(exceptionInfo),
           generateDelegate);
  }

  private void doTestForGroovy(String newVisibility,
                      String newName,
                      String newReturnType,
                      GenParams genParams,
                      GenExceptions genExceptions,
                      final boolean generateDelegate) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    executeRefactoring(newVisibility, newName, newReturnType, genParams, genExceptions, generateDelegate);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void doTestForJava(String newVisibility,
                             String newName,
                             String newReturnType,
                             GenParams genParams,
                             GenExceptions genExceptions,
                             final boolean generateDelegate) throws Exception {
    myFixture.configureByFiles(getTestName(false) + ".groovy", getTestName(false)+".java");
    executeRefactoring(newVisibility, newName, newReturnType, genParams, genExceptions, generateDelegate);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void executeRefactoring(String newVisibility,
                                  String newName,
                                  String newReturnType,
                                  GenParams genParams,
                                  GenExceptions genExceptions, boolean generateDelegate) {
    final PsiElement targetElement =
      TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof GrMethod);
    GrMethod method = (GrMethod)targetElement;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiType newType;
    if (newReturnType == null) {
      newType = method.getReturnType();
    }
    else if (newReturnType.length() == 0) {
      newType = null;
    }
    else {
      newType = factory.createTypeFromText(newReturnType, method);
    }
    GrChangeInfoImpl changeInfo =
      new GrChangeInfoImpl(method, newVisibility, newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
                           newName != null ? newName : method.getName(), Arrays.asList(genParams.genParams(method)),
                           genExceptions.genExceptions(method), generateDelegate);
    new GrChangeSignatureProcessor(getProject(), changeInfo).run();
  }


  private interface GenParams {
    GrParameterInfo[] genParams(GrMethod method) throws IncorrectOperationException;
  }

  private class SimpleParameterGen implements GenParams {
    private final SimpleInfo[] myInfos;

    private SimpleParameterGen(SimpleInfo[] infos) {
      myInfos = infos;
    }

    public GrParameterInfo[] genParams(GrMethod method) {
      GrParameter[] params = method.getParameterList().getParameters();
      GrParameterInfo[] result=new GrParameterInfo[myInfos.length];
      for (int i = 0; i < myInfos.length; i++) {
        final SimpleInfo sim = myInfos[i];
        int oldIndex = sim.myOldIndex;
        final GrParameterInfo info;
        if (oldIndex > -1) {
          info = new GrParameterInfo(params[oldIndex], oldIndex);
        }
        else {
          info = new GrParameterInfo(getProject(), method);
        }
        if (sim.myNewName != null) {
          setText(info.getNameFragment(), sim.myNewName);
        }
        if (sim.myType != null) {
          setText(info.getTypeFragment(), sim.myType.getCanonicalText());          
        }
        if (sim.myDefaultInitializer != null) {
          setText(info.getDefaultInitializerFragment(), sim.myDefaultInitializer);
        }
        if (sim.myDefaultValue != null) {
          setText(info.getDefaultValueFragment(), sim.myDefaultValue);
        }
        result[i] = info;
      }
      return result;
    }

    private void setText(final PsiCodeFragment codeFragment, final String newText) {
      new WriteAction() {
        @Override
        protected void run(Result result) throws Throwable {
          final PsiDocumentManager docManager = PsiDocumentManager.getInstance(getProject());
          final Document document = docManager.getDocument(codeFragment);
          document.setText(newText);
          docManager.commitDocument(document);
        }
      }.execute();
    }
  }

  private interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleExceptionsGen implements GenExceptions {
    private final ThrownExceptionInfo[] myInfos;

    public SimpleExceptionsGen() {
      myInfos = new ThrownExceptionInfo[0];
    }

    private SimpleExceptionsGen(ThrownExceptionInfo[] infos) {
      myInfos = infos;
    }

    public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
      for (ThrownExceptionInfo info : myInfos) {
        info.updateFromMethod(method);
      }
      return myInfos;
    }
  }

  static class SimpleInfo {
    int myOldIndex;
    String myNewName;
    String myDefaultValue;
    String myDefaultInitializer;
    private PsiType myType;

    SimpleInfo(int oldIndex) {
      this(null, oldIndex);
    }

    SimpleInfo(String newName, int oldIndex) {
      this(newName, oldIndex, "", null, null);
    }

    SimpleInfo(String newName, int oldIndex, String defaultValue, String defaultInitializer, PsiType type) {
      myOldIndex = oldIndex;
      myNewName = newName;
      myDefaultValue = defaultValue;
      myDefaultInitializer = defaultInitializer;
      myType = type;
    }
  }
}
