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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult {
  private PsiElement myElement;
  private boolean myIsAccessible;
  private boolean myIsStaticsOK;
  private PsiSubstitutor mySubstitutor;

  private GroovyPsiElement myCurrentFileResolveContext;

  public GroovyResolveResultImpl(PsiElement element, boolean isAccessible) {
    this(element, null, PsiSubstitutor.EMPTY, isAccessible, true);
  }

  public GroovyResolveResultImpl(PsiElement element,
                                 GroovyPsiElement context,
                                 PsiSubstitutor substitutor,
                                 boolean isAccessible,
                                 boolean staticsOK) {
    myCurrentFileResolveContext = context;
    myElement = element instanceof PsiClass? GrClassSubstitutor.getSubstitutedClass((PsiClass)element) : element;
    myIsAccessible = isAccessible;
    mySubstitutor = substitutor;
    myIsStaticsOK = staticsOK;
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
}
