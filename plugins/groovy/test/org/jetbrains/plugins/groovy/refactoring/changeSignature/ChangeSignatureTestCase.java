/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
public abstract class ChangeSignatureTestCase extends LightCodeInsightFixtureTestCase {
  void executeRefactoring(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                          @Nullable String newName,
                          @Nullable String newReturnType,
                          @NotNull GenParams genParams,
                          @NotNull GenExceptions genExceptions,
                          boolean generateDelegate) {
    final PsiElement targetElement = new GrChangeSignatureHandler().findTargetMember(myFixture.getFile(), myFixture.getEditor());
      //TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
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
      new GrChangeInfoImpl(method,
                           newVisibility,
                           newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
                           newName != null ? newName : method.getName(),
                           Arrays.asList(genParams.genParams(method)),
                           genExceptions.genExceptions(method),
                           generateDelegate);
    new GrChangeSignatureProcessor(getProject(), changeInfo).run();
  }


  interface GenParams {
    GrParameterInfo[] genParams(GrMethod method) throws IncorrectOperationException;
  }

  interface GenExceptions {
    ThrownExceptionInfo[] genExceptions(PsiMethod method) throws IncorrectOperationException;
  }
}
