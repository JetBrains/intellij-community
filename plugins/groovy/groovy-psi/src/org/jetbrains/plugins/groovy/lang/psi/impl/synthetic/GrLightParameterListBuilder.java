// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GrLightParameterListBuilder extends LightElement implements GrParameterList {
  private final List<GrParameter> myParameters = new ArrayList<>();
  private GrParameter[] myCachedParameters;

  public GrLightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public GrParameter addParameter(@NotNull GrParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
    return parameter;
  }

  @Override
  public String toString() {
    return "GrLightParameterListBuilder";
  }

  @Override
  public GrParameter @NotNull [] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = GrParameter.EMPTY_ARRAY;
      }
      else {
        myCachedParameters = myParameters.toArray(GrParameter.EMPTY_ARRAY);
      }
    }
    
    return myCachedParameters;
  }

  public void copyParameters(@NotNull PsiMethod method, PsiSubstitutor substitutor, PsiMethod scope) {
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      GrLightParameter p = new GrLightParameter(parameter.getName(), substitutor.substitute(parameter.getType()), scope);

      if (parameter instanceof GrParameter) {
        p.setOptional(((GrParameter)parameter).isOptional());
      }

      addParameter(p);
    }
  }

  @Override
  public int getParameterNumber(GrParameter parameter) {
    return getParameterIndex(parameter);
  }

  @Override
  public @Nullable PsiElement getLParen() {
    return null;
  }

  @Override
  public @Nullable PsiElement getRParen() {
    return null;
  }

  @Override
  public @NotNull TextRange getParametersRange() {
    throw new IllegalStateException();
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    //noinspection SuspiciousMethodCalls
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {

  }

  public @NotNull GrParameter removeParameter(int index) {
    GrParameter removed = myParameters.remove(index);
    myCachedParameters = null;
    return removed;
  }

  public void clear() {
    myParameters.clear();
    myCachedParameters = null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GrLightParameterListBuilder builder = (GrLightParameterListBuilder)o;
    return Objects.equals(myParameters, builder.myParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myParameters);
  }
}
