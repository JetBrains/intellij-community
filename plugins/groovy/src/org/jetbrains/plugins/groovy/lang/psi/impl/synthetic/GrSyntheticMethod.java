/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class GrSyntheticMethod extends LightElement implements PsiMethod {
  private final LightReferenceList myThrowsList;
  private LightParameterList myParameterList = null;
  private LightModifierList myModifierList = null;
  private final String myName;

  protected GrSyntheticMethod(PsiManager manager, String name) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myName = name;
    myThrowsList = new LightReferenceList(manager);
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  protected abstract LightParameter[] getParameters();

  @NotNull
  public PsiParameterList getParameterList() {
    final PsiManager manager = getManager();
    if (myParameterList == null) {
      myParameterList = new LightParameterList(manager, new Computable<LightParameter[]>() {
        public LightParameter[] compute() {
          return getParameters();
        }
      });
    }
    return myParameterList;
  }

  protected abstract Set<String> getModifiers();

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  @Nullable
  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return new PsiMethod[0];
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    //do nothing
    return null;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public TextRange getTextRange() {
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    //do nothing
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @NotNull
  public PsiModifierList getModifierList() {
    if (myModifierList == null) {
      myModifierList = new LightModifierList(getManager(), getModifiers());
    }

    return myModifierList;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return new PsiMethod[0];
  }
  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }
}
