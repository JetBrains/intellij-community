/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeInfoImpl implements JavaChangeInfo {
  private GrMethod method;
  private final String newName;
  @Nullable private final CanonicalTypes.Type returnType;
  private final String visibilityModifier;
  private final List<? extends GrParameterInfo> parameters;
  private final boolean myAreParametersChanged;
  private final boolean myIsParameterTypesChanged;
  private final boolean myIsParameterNamesChanged;
  private final boolean myIsNameChanged;
  private final boolean myIsVisibilityChanged;
  private final boolean myIsReturnTypeChanged;
  private final boolean myIsRetainVarargs;
  private final boolean myIsArrayToVarargs;
  private final boolean myIsObtainVarargs;
  private final boolean myWasVarargs;
  private final String myOldName;
  private final PsiIdentifier myNewNameIdentifier;
  private final PsiExpression[] defaultValues;
  private final boolean myDelegate;
  private final ThrownExceptionInfo[] myThrownExceptions;
  private final boolean myExceptionSetChanged;
  private final boolean myExceptionSetOrOrderChanged;
  private final String[] myOldParameterNames;
  private final String[] myOldParameterTypes;

  public GrChangeInfoImpl(@NotNull GrMethod method,
                          @Nullable String visibilityModifier,
                          @Nullable CanonicalTypes.Type returnType,
                          String newName,
                          List<? extends GrParameterInfo> parameters, ThrownExceptionInfo @Nullable [] exceptions, boolean generateDelegate) {
    this.method = method;
    this.visibilityModifier = visibilityModifier == null ? VisibilityUtil.getVisibilityModifier(method.getModifierList()) : visibilityModifier;
    this.returnType = returnType;
    this.parameters = parameters;
    this.newName = newName;
    myDelegate = generateDelegate;
    myOldName = method.getName();

    myIsNameChanged = !method.getName().equals(newName);

    myIsVisibilityChanged = visibilityModifier != null && !method.hasModifierProperty(visibilityModifier);

    boolean isReturnTypeChanged = false;
    if (!method.isConstructor()) {
      PsiType oldReturnType = null;
      if (method.getReturnTypeElementGroovy() != null) {
        oldReturnType = method.getReturnType();
      }
      try {
        PsiType newReturnType = returnType == null ? null : returnType.getType(method);
        if (!Objects.equals(oldReturnType, newReturnType)) {
          isReturnTypeChanged = true;
        }
      }
      catch (IncorrectOperationException e) {
        isReturnTypeChanged = true;
      }
    }
    myIsReturnTypeChanged = isReturnTypeChanged;

    GrParameter[] params = method.getParameters();
    final int oldParameterCount = this.method.getParameters().length;
    myOldParameterNames = new String[oldParameterCount];
    myOldParameterTypes = new String[oldParameterCount];

    for (int i = 0; i < oldParameterCount; i++) {
      GrParameter param = params[i];
      myOldParameterNames[i] = param.getName();
      myOldParameterTypes[i] = param.getType().getCanonicalText();
    }

    boolean isParameterNamesChanged = false;
    boolean isParameterTypesChanged = false;
    boolean areParametersChanged = false;
    if (oldParameterCount != this.parameters.size()) {
      areParametersChanged = true;
    }
    else {
      for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
        GrParameterInfo parameter = parameters.get(i);
        if (parameter.getOldIndex() != i) {
          areParametersChanged = true;
          break;
        }
        if (!params[i].getName().equals(parameter.getName())) {
          isParameterNamesChanged = true;
        }
        try {
          PsiType type = parameter.createType(method);
          PsiType oldType = params[i].getType();
          if (!oldType.equals(type)) {
            isParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          isParameterTypesChanged = true;
        }
      }
    }
    myIsParameterNamesChanged = isParameterNamesChanged;
    myIsParameterTypesChanged = isParameterTypesChanged;
    myAreParametersChanged = areParametersChanged;

    myWasVarargs = method.isVarArgs();
    if (parameters.isEmpty()) {
      myIsObtainVarargs = false;
      myIsRetainVarargs = false;
      myIsArrayToVarargs = false;
    }
    else {
      GrParameterInfo lastNewParam = parameters.get(parameters.size() - 1);
      myIsObtainVarargs = lastNewParam.isVarargType();
      myIsRetainVarargs = lastNewParam.getOldIndex() >= 0 && myIsObtainVarargs;
      if (myIsRetainVarargs) {
        final PsiType oldTypeForVararg = params[lastNewParam.getOldIndex()].getType();
        myIsArrayToVarargs = oldTypeForVararg instanceof PsiArrayType && !(oldTypeForVararg instanceof PsiEllipsisType);
      }
      else {
        myIsArrayToVarargs = false;
      }
    }

    if (myIsNameChanged) {
      if (StringUtil.isJavaIdentifier(newName)) {
        myNewNameIdentifier = JavaPsiFacade.getElementFactory(getMethod().getProject()).createIdentifier(newName);
      }
      else {
        myNewNameIdentifier = getMethod().getNameIdentifier();
      }
    }
    else {
      myNewNameIdentifier = null;
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    defaultValues = new PsiExpression[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      GrParameterInfo info = parameters.get(i);
      if (info.getOldIndex() < 0 && !info.isVarargType()) {
        try {
          defaultValues[i] = factory.createExpressionFromText(info.getDefaultValue(), method);
        }
        catch (IncorrectOperationException ignored) {
        }
      }
    }

    myThrownExceptions = exceptions;
    if (exceptions == null) {
      myExceptionSetChanged = false;
      myExceptionSetOrOrderChanged = false;
    }
    else {
      final PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
      if (thrownTypes.length != myThrownExceptions.length) {
        myExceptionSetChanged = true;
        myExceptionSetOrOrderChanged = true;
      }
      else {
        boolean exceptionSetChanged = false;
        boolean exceptionSetOrOrderChanged = false;
        for (int i = 0; i < myThrownExceptions.length; i++) {
          ThrownExceptionInfo info = myThrownExceptions[i];
          if (info.getOldIndex() < 0 || !thrownTypes[info.getOldIndex()].equals(info.createType(method, method.getManager()))) {
            exceptionSetChanged = true;
            exceptionSetOrOrderChanged = true;
            break;
          }
          else if (info.getOldIndex() != i) {
            exceptionSetOrOrderChanged = true;
          }
        }
        myExceptionSetChanged = exceptionSetChanged;
        myExceptionSetOrOrderChanged = exceptionSetOrOrderChanged;
      }
    }
  }

  @Override
  public JavaParameterInfo @NotNull [] getNewParameters() {
    return parameters.toArray(new GrParameterInfo[0]);
  }

  @NotNull
  @Override
  public String getNewVisibility() {
    return visibilityModifier;
  }

  @Override
  public boolean isParameterSetOrOrderChanged() {
    return myAreParametersChanged;
  }

  @Override
  public boolean isParameterTypesChanged() {
    return myIsParameterTypesChanged;
  }

  @Override
  public boolean isParameterNamesChanged() {
    return myIsParameterNamesChanged;
  }

  @Override
  public boolean isGenerateDelegate() {
    return myDelegate;
  }

  @Override
  public boolean isNameChanged() {
    return myIsNameChanged;
  }

  @Override
  public boolean isVisibilityChanged() {
    return myIsVisibilityChanged;
  }

  @Override
  public boolean isExceptionSetChanged() {
    return myExceptionSetChanged;
  }

  @Override
  public boolean isExceptionSetOrOrderChanged() {
    return myExceptionSetOrOrderChanged;
  }

  @Override
  public @NotNull GrMethod getMethod() {
    return method;
  }

  @Override
  public boolean isReturnTypeChanged() {
    return myIsReturnTypeChanged;
  }

  @Override
  public CanonicalTypes.Type getNewReturnType() {
    return returnType;
  }

  @Override
  public String getNewName() {
    return newName;
  }

  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Override
  public String @NotNull [] getOldParameterNames() {
    return myOldParameterNames;
  }

  @Override
  public String @NotNull [] getOldParameterTypes() {
    return myOldParameterTypes;
  }

  @Override
  public ThrownExceptionInfo[] getNewExceptions() {
    return myThrownExceptions;
  }

  @Override
  public boolean isRetainsVarargs() {
    return myIsRetainVarargs;
  }

  @Override
  public boolean isObtainsVarags() {
    return myIsObtainVarargs;
  }

  @Override
  public boolean isArrayToVarargs() {
    return myIsArrayToVarargs;
  }

  @Override
  public PsiIdentifier getNewNameIdentifier() {
    return myNewNameIdentifier;
  }

  @Override
  public String getOldName() {
    return myOldName;
  }

  @Override
  public boolean wasVararg() {
    return myWasVarargs;
  }

  @Override
  public boolean[] toRemoveParm() {
    return new boolean[0];
  }

  @Override
  public PsiExpression getValue(int i, PsiCallExpression callExpression) {
    if (defaultValues[i] != null) return defaultValues[i];
    final PsiElement valueAtCallSite = parameters.get(i).getActualValue(callExpression, PsiSubstitutor.EMPTY);
    return valueAtCallSite instanceof PsiExpression ? (PsiExpression)valueAtCallSite : null;
  }

  @Override
  public void updateMethod(@NotNull PsiMethod psiMethod) {
    if (psiMethod instanceof GrMethod) {
      method = (GrMethod)psiMethod;
    }
  }

  @Override
  public @NotNull Collection<PsiMethod> getMethodsToPropagateParameters() {
    return Collections.emptyList();
  }
}
