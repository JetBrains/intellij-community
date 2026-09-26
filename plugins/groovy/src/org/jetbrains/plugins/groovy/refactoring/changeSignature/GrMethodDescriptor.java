// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.changeSignature.MethodDescriptor;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrMethodDescriptor implements MethodDescriptor<GrParameterInfo, String> {
  private final GrMethod myMethod;

  public GrMethodDescriptor(@NotNull GrMethod method) {
    myMethod = method;
  }

  @Override
  public String getName() {
    return myMethod.getName();
  }

  @Override
  public @NotNull List<GrParameterInfo> getParameters() {
    final ArrayList<GrParameterInfo> result = new ArrayList<>();
    final GrParameter[] parameters = myMethod.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      GrParameterInfo info = new GrParameterInfo(parameter, i);
      result.add(info);
    }
    return result;
  }

  @Override
  public int getParametersCount() {
    return myMethod.getParameterList().getParametersCount();
  }

  @Override
  public @NotNull String getVisibility() {
    return VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
  }

  @Override
  public @NotNull GrMethod getMethod() {
    return myMethod;
  }

  @Override
  public boolean canChangeVisibility() {
    PsiClass containingClass = myMethod.getContainingClass();
    return containingClass != null && !containingClass.isInterface();
  }

  @Override
  public boolean canChangeParameters() {
    return true;
  }

  @Override
  public boolean canChangeName() {
    return !myMethod.isConstructor();
  }

  @Override
  public @NotNull ReadWriteOption canChangeReturnType() {
    return myMethod.isConstructor() ? ReadWriteOption.None : ReadWriteOption.ReadWrite;
  }

  public String getReturnTypeText() {
    GrTypeElement returnTypeElement = myMethod.getReturnTypeElementGroovy();
    return returnTypeElement != null ? returnTypeElement.getText() : "";
  }
}
