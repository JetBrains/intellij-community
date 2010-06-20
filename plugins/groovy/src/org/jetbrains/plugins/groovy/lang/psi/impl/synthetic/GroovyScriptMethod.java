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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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

/**
 * @author ven
 */
public class GroovyScriptMethod extends LightElement implements PsiMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptMethod");
  public PsiMethod myCodeBehindMethod;
  private final GroovyScriptClass myScriptClass;

  public GroovyScriptMethod(GroovyScriptClass scriptClass, String codeBehindText) {
    super(scriptClass.getManager(), GroovyFileType.GROOVY_LANGUAGE);
    myScriptClass = scriptClass;
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    try {
      myCodeBehindMethod = factory.createMethodFromText(codeBehindText, null);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public PsiType getReturnType() {
    return myCodeBehindMethod.getReturnType();
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myCodeBehindMethod.getParameterList();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myCodeBehindMethod.getThrowsList();
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
    return myCodeBehindMethod.getSignature(substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return new PsiMethod[0];
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

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myCodeBehindMethod.getModifierList();
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myCodeBehindMethod.hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    return myCodeBehindMethod.getName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set name");
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myCodeBehindMethod.getHierarchicalMethodSignature();
  }

  public PsiClass getContainingClass() {
    return myScriptClass;
  }

  public PsiFile getContainingFile() {
    return myScriptClass.getContainingFile();
  }

  public TextRange getTextRange() {
    return myScriptClass.getTextRange();
  }

  public String toString() {
    return "GroovyScriptMethod";
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
  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  public PsiElement getContext() {
    return myScriptClass;
  }
}
