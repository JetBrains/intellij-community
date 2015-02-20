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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameter;
import org.jetbrains.annotations.NotNull;
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
  private volatile boolean myNavigationElementInit;

  protected GrMethodWrapper(PsiMethod method, PsiSubstitutor substitutor) {
    super(method.getManager(), method.getName());
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

  @NotNull
  @Override
  public PsiMethod getPrototype() {
    return myWrappedMethod;
  }
}
