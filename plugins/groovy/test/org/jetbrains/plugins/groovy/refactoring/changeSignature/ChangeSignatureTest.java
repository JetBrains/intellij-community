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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
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
    doTest(null, new GrParameterInfo[]{
      new SimpleInfo("p", -1, "\"5\"", null, createType(CommonClassNames.JAVA_LANG_STRING))
    });
  }

  public void testRemoveParameter() throws Exception {
    doTest(null, new GrParameterInfo[0]);
  }

  public void testInsertParameter() throws Exception {
    doTest(null, new GrParameterInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", "-3", createType("int")),
      new SimpleInfo(1)
    });
  }

  public void testInsertOptionalParameter() throws Exception {
    doTest(null, new GrParameterInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(1),
      new SimpleInfo("p", -1, "5", "-3", createType("int"))
    });
  }

  public void testNamedParametersRemove() throws Exception {
    doTest(null, new GrParameterInfo[]{
      new SimpleInfo(1),
      new SimpleInfo(2)
    });
  }

  public void testNamedParametersOrder1() throws Exception {
    doTest(null, new GrParameterInfo[] {
      new SimpleInfo(0),
      new SimpleInfo(2)
    });
  }

  public void testNamedParametersOrder2() throws Exception {
    doTest(null, new GrParameterInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, createType("int")),
      new SimpleInfo(2),
    });
  }
  
  public void testNamedParametersOrder3() throws Exception {
    doTest(null, new GrParameterInfo[] {
      new SimpleInfo(0),
      new SimpleInfo(2),
      new SimpleInfo("p", -1, "5", null, createType("int")),
    });
  }

  public void testMoveNamedParameters() throws Exception {
    doTest(null, new GrParameterInfo[] {
      new SimpleInfo(1),
      new SimpleInfo(0)
    });
  }

  public void testMoveVarArgParameters() throws Exception {
    doTest(null, new GrParameterInfo[] {
      new SimpleInfo(1),
      new SimpleInfo(0)
    });
  }

  public void testChangeVisibilityAndName() throws Exception {
    doTest("protected", "newName", null, new GrParameterInfo[]{new SimpleInfo(0)}, new ThrownExceptionInfo[0], false);
  }

  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(getProject()));
  }


  private void doTest(String newReturnType, GrParameterInfo[] parameterInfos) throws Exception {
    doTest("public", null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], false);
  }

  private void doTest(String newReturnType, GrParameterInfo[] parameterInfos, final boolean generateDelegate) throws Exception {
    doTest("public", null, newReturnType, parameterInfos, new ThrownExceptionInfo[0], generateDelegate);
  }

  private void doTest(String newVisibility,
                      String newName,
                      String newReturnType,
                      GrParameterInfo[] parameterInfo,
                      ThrownExceptionInfo[] exceptionInfo,
                      final boolean generateDelegate) throws Exception {
    doTest(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo), new SimpleExceptionsGen(exceptionInfo),
           generateDelegate);
  }

  private void doTest(String newVisibility, String newName, @NonNls String newReturnType, GenParams gen, final boolean generateDelegate)
    throws Exception {
    doTest(newVisibility, newName, newReturnType, gen, new SimpleExceptionsGen(), generateDelegate);
  }

  private void doTest(String newVisibility,
                      String newName,
                      String newReturnType,
                      GenParams genParams,
                      GenExceptions genExceptions,
                      final boolean generateDelegate) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final PsiElement targetElement =
      TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof GrMethod);
    GrMethod method = (GrMethod)targetElement;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType();
    GrChangeInfoImpl changeInfo =
      new GrChangeInfoImpl(method, newVisibility, CanonicalTypes.createTypeWrapper(newType), newName != null ? newName : method.getName(),
                           Arrays.asList(genParams.genParams(method)));
    new GrChangeSignatureProcessor(getProject(), changeInfo).run();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }


  private interface GenParams {
    GrParameterInfo[] genParams(GrMethod method) throws IncorrectOperationException;
  }

  private static class SimpleParameterGen implements GenParams {
    private final GrParameterInfo[] myInfos;

    private SimpleParameterGen(GrParameterInfo[] infos) {
      myInfos = infos;
    }

    public GrParameterInfo[] genParams(GrMethod method) {
      GrParameter[] params = method.getParameterList().getParameters();
      for (int i = 0, myInfosLength = myInfos.length; i < myInfosLength; i++) {
        int oldIndex = myInfos[i].getOldIndex();
        if (oldIndex > -1) {
          myInfos[i] = new GrParameterInfo(params[oldIndex], oldIndex);
        }
      }
      return myInfos;
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

  class SimpleInfo extends GrParameterInfo {
    int myOldIndex;
    String myNewName;
    String myDefaultValue;
    String myDefaultInitializer;
    CanonicalTypes.Type myTypeWrapper;

    SimpleInfo(int oldIndex) {
      this(null, oldIndex);
    }

    SimpleInfo(String newName, int oldIndex) {
      this(newName, oldIndex, "", null, null);
    }

    SimpleInfo(
      String newName,
      int oldIndex,
      String defaultValue,
      String defaultInitializer,
      PsiType type) {
      super(myFixture.getProject(), myFixture.getFile());
      myOldIndex = oldIndex;
      myNewName = newName;
      myDefaultValue = defaultValue;
      if (type == null) {
        myTypeWrapper = null;
      }
      else {
        myTypeWrapper = CanonicalTypes.createTypeWrapper(type);
      }
      myDefaultInitializer = defaultInitializer;
    }

    @Override
    public int getOldIndex() {
      return myOldIndex;
    }

    @Override
    public String getName() {
      return myNewName;
    }

    @Override
    public String getDefaultValue() {
      return myDefaultValue;
    }

    @Override
    public CanonicalTypes.Type getTypeWrapper() {
      return myTypeWrapper;
    }

    @Override
    public String getTypeText() {
      return myTypeWrapper.getTypeText();
    }

    @Override
    public PsiType createType(PsiElement context, PsiManager manager) throws IncorrectOperationException {
      return myTypeWrapper.getType(context, manager);
    }

    @Override
    public String getDefaultInitializer() {
      return myDefaultInitializer;
    }
  }
}
