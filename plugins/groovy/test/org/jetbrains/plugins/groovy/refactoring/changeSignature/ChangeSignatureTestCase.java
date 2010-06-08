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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
public abstract class ChangeSignatureTestCase extends LightCodeInsightFixtureTestCase {
  void executeRefactoring(String newVisibility,
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
      if (method.getReturnTypeElement() == null) {
        newType = null;
      }
      else {
        newType = method.getReturnType();
      }
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


  interface GenParams {
    GrParameterInfo[] genParams(GrMethod method) throws IncorrectOperationException;
  }

  static class SimpleParameterGen implements GenParams {
    private final SimpleInfo[] myInfos;
    private Project myProject;

    public SimpleParameterGen(SimpleInfo[] infos, Project project) {
      myInfos = infos;
      myProject = project;
    }

    public GrParameterInfo[] genParams(GrMethod method) {
      GrParameter[] params = method.getParameterList().getParameters();
      GrParameterInfo[] result = new GrParameterInfo[myInfos.length];
      for (int i = 0; i < myInfos.length; i++) {

        final SimpleInfo sim = myInfos[i];
        int oldIndex = sim.myOldIndex;

        final GrParameterInfo info;
        String name = null;
        String defInitializer = null;
        PsiType type = null;
        String defValue = null;
        if (oldIndex > -1) {
          final GrParameter p = params[oldIndex];
          name = p.getName();
          final GrExpression initializer = p.getDefaultInitializer();
          defInitializer = initializer != null ? initializer.getText() : null;
          type = p.getDeclaredType();
        }

        if (sim.myNewName != null) {
          name = sim.myNewName;
        }
        if (sim.myType != null && sim.myType.length() > 0) {
          type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(sim.myType, method);
        }
        if (sim.myDefaultInitializer != null) {
          defInitializer = sim.myDefaultInitializer;
        }
        if (sim.myDefaultValue != null) {
          defValue = sim.myDefaultValue;
        }

        assert (oldIndex < 0 && defValue != null) || oldIndex >= 0;
        assert name != null;
        info = new GrParameterInfo(name, defValue, defInitializer, type, oldIndex);
        result[i] = info;
      }
      return result;
    }
  }

  interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  static class SimpleExceptionsGen implements GenExceptions {
    private final ThrownExceptionInfo[] myInfos;

    public SimpleExceptionsGen() {
      myInfos = new ThrownExceptionInfo[0];
    }

    public SimpleExceptionsGen(ThrownExceptionInfo[] infos) {
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
    private String myType;

    SimpleInfo(int oldIndex) {
      this(null, oldIndex);
    }

    SimpleInfo(String newName, int oldIndex) {
      this(newName, oldIndex, "", null, "");
    }

    SimpleInfo(String newName, int oldIndex, String defaultValue, String defaultInitializer, String type) {
      myOldIndex = oldIndex;
      myNewName = newName;
      myDefaultValue = defaultValue;
      myDefaultInitializer = defaultInitializer;
      myType = type;
    }

    SimpleInfo(String newName, int oldIndex, String defaultValue, String defaultInitializer, PsiType type) {
      this(newName, oldIndex, defaultValue, defaultInitializer, type.getCanonicalText());
    }
  }
}
