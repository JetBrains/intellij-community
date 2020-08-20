// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrPsiTypeStub;

/**
 * @author Sergey Evdokimov
 */
public class GrMethodWrapper extends GrLightMethodBuilder implements PsiMirrorElement {
  private static final PsiType TYPE_MARKER = new GrPsiTypeStub() {
    @Override
    public boolean isValid() {
      return false;
    }
  };

  private final PsiMethod myWrappedMethod;
  private PsiElement myContext;
  private volatile boolean myNavigationElementInit;

  protected GrMethodWrapper(PsiMethod method, PsiSubstitutor substitutor) {
    this(method, substitutor, method.getName());
  }

  protected GrMethodWrapper(PsiMethod method, PsiSubstitutor substitutor, String name) {
    super(method.getManager(), name);
    myWrappedMethod = method;
    setContainingClass(method.getContainingClass());
    getModifierList().copyModifiers(method);
    getParameterList().copyParameters(method, substitutor, this);
    for (PsiTypeParameter parameter : method.getTypeParameters()) {
      getTypeParameterList().addParameter(new LightTypeParameter(parameter));
    }
    if (method instanceof OriginInfoAwareElement) {
      setOriginInfo(((OriginInfoAwareElement)method).getOriginInfo());
    }

    setReturnType(TYPE_MARKER);
  }

  @Override
  public PsiElement getContext() {
    if (myContext != null) return myContext;
    return super.getContext();
  }

  public void setContext(@Nullable PsiElement context) {
    myContext = context;
  }

  @Override
  public void setNavigationElement(@NotNull PsiElement navigationElement) {
    myNavigationElementInit = true;
    super.setNavigationElement(navigationElement);
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    if (!myNavigationElementInit) {
      setNavigationElement(myWrappedMethod.getNavigationElement()); // getNavigationElement() can get long time if wrapped method is a ClsMethod.
    }
    return super.getNavigationElement();
  }

  @Override
  public PsiType getReturnType() {
    PsiType type = super.getReturnType();
    if (type == TYPE_MARKER) {
      type = myWrappedMethod.getReturnType();
      super.setReturnType(type);
    }

    return type;
  }

  @Override
  public boolean isValid() {
    if (myNavigationElementInit) {
      return super.isValid(); // This will call isValid() on navigationElement
    }

    return myWrappedMethod.isValid();
  }

  public static GrMethodWrapper wrap(@NotNull PsiMethod method) {
    return new GrMethodWrapper(method, PsiSubstitutor.EMPTY);
  }

  public static GrLightMethodBuilder wrap(GrMethod method, PsiSubstitutor substitutor) {
    return new GrMethodWrapper(method, substitutor);
  }

  public static GrMethodWrapper wrap(@NotNull PsiMethod method, @NlsSafe @NotNull String newName) {
    return new GrMethodWrapper(method, PsiSubstitutor.EMPTY, newName);
  }

  @NotNull
  @Override
  public PsiMethod getPrototype() {
    return myWrappedMethod;
  }
}
