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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult {
  private final PsiElement myElement;
  private final boolean myIsAccessible;
  private final boolean myIsStaticsOK;
  private final PsiSubstitutor mySubstitutor;
  private final boolean myIsInvokedOnProperty;

  private GroovyPsiElement myCurrentFileResolveContext;

  public GroovyResolveResultImpl(PsiElement element, boolean isAccessible) {
    this(element, null, PsiSubstitutor.EMPTY, isAccessible, true);
  }

  public GroovyResolveResultImpl(PsiElement element,
                                 @Nullable GroovyPsiElement context,
                                 PsiSubstitutor substitutor,
                                 boolean isAccessible,
                                 boolean staticsOK) {
    this(element, context, substitutor, isAccessible, staticsOK, false);
  }

  public GroovyResolveResultImpl(PsiClassType.ClassResolveResult classResolveResult) {
    this(classResolveResult.getElement(), null, classResolveResult.getSubstitutor(), classResolveResult.isAccessible(),
         classResolveResult.isStaticsScopeCorrect());
  }

  public GroovyResolveResultImpl(PsiElement element,
                                 GroovyPsiElement resolveContext,
                                 PsiSubstitutor substitutor,
                                 boolean isAccessible,
                                 boolean staticsOK,
                                 boolean isInvokedOnProperty) {
    myCurrentFileResolveContext = resolveContext;
    myElement = element instanceof PsiClass? GrClassSubstitutor.getSubstitutedClass((PsiClass)element) : element;
    myIsAccessible = isAccessible;
    mySubstitutor = substitutor;
    myIsStaticsOK = staticsOK;
    myIsInvokedOnProperty = isInvokedOnProperty;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public boolean isAccessible() {
    return myIsAccessible;
  }

  public boolean isStaticsOK() {
    return myIsStaticsOK;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return isAccessible();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyResolveResultImpl that = (GroovyResolveResultImpl) o;

    return myIsAccessible == that.myIsAccessible &&
        myElement.getManager().areElementsEquivalent(myElement, that.myElement);

  }

  public int hashCode() {
    int result = 0;
    if (myElement instanceof PsiNamedElement) {
      String name = ((PsiNamedElement) myElement).getName();
      if (name != null) {
        result = name.hashCode();
      }
    }
    result = 31 * result + (myIsAccessible ? 1 : 0);
    return result;
  }

  public GroovyPsiElement getCurrentFileResolveContext() {
    return myCurrentFileResolveContext;
  }

  public boolean isInvokedOnProperty() {
    return myIsInvokedOnProperty;
  }

  @Override
  public String toString() {
    return "GroovyResolveResultImpl{" +
           "myElement=" + myElement +
           ", mySubstitutor=" + mySubstitutor +
           '}';
  }
}
