// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.EmptyGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult {
  private final @NotNull PsiElement myElement;
  private final boolean myIsAccessible;
  private final boolean myIsStaticsOK;
  private final boolean myIsApplicable;

  private final @NotNull PsiSubstitutor mySubstitutor;
  private final boolean myIsInvokedOnProperty;

  private final @Nullable PsiElement myCurrentFileResolveContext;
  private final @Nullable SpreadState mySpreadState;

  public GroovyResolveResultImpl(@NotNull PsiElement element, boolean isAccessible) {
    this(element, null, null, PsiSubstitutor.EMPTY, isAccessible, true, false, true);
  }

  public GroovyResolveResultImpl(@NotNull PsiElement element,
                                 @Nullable PsiElement resolveContext,
                                 @Nullable SpreadState spreadState,
                                 @NotNull PsiSubstitutor substitutor,
                                 boolean isAccessible,
                                 boolean staticsOK) {
    this(element, resolveContext, spreadState, substitutor, isAccessible, staticsOK, false, true);
  }

  public GroovyResolveResultImpl(@NotNull PsiElement element,
                                 @Nullable PsiElement resolveContext,
                                 @Nullable SpreadState spreadState,
                                 @NotNull PsiSubstitutor substitutor,
                                 boolean isAccessible,
                                 boolean staticsOK,
                                 boolean isInvokedOnProperty,
                                 boolean isApplicable) {
    myCurrentFileResolveContext = resolveContext;
    myElement = element;
    myIsAccessible = isAccessible;
    mySubstitutor = substitutor;
    myIsStaticsOK = staticsOK;
    myIsInvokedOnProperty = isInvokedOnProperty;
    mySpreadState = spreadState;
    myIsApplicable = isApplicable;
  }

  @NotNull
  @Override
  public PsiSubstitutor getContextSubstitutor() {
    return mySubstitutor;
  }

  @Override
  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  public boolean isAccessible() {
    return myIsAccessible;
  }

  @Override
  public boolean isStaticsOK() {
    return myIsStaticsOK;
  }

  @Override
  public boolean isApplicable() {
    return myIsApplicable;
  }

  @Override
  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  public boolean isValidResult() {
    return isAccessible() && isApplicable() && isStaticsOK();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyResolveResultImpl that = (GroovyResolveResultImpl)o;

    return myIsAccessible == that.myIsAccessible &&
           myElement.getManager().areElementsEquivalent(myElement, that.myElement);
  }

  @Override
  public int hashCode() {
    int result = 0;
    if (myElement instanceof PsiNamedElement) {
      String name = ((PsiNamedElement)myElement).getName();
      if (name != null) {
        result = name.hashCode();
      }
    }
    result = 31 * result + (myIsAccessible ? 1 : 0);
    return result;
  }

  @Override
  @Nullable
  public PsiElement getCurrentFileResolveContext() {
    return myCurrentFileResolveContext;
  }

  @Override
  public boolean isInvokedOnProperty() {
    return myIsInvokedOnProperty;
  }

  @Nullable
  @Override
  public SpreadState getSpreadState() {
    return mySpreadState;
  }

  @Override
  public String toString() {
    return "GroovyResolveResultImpl{" +
           "myElement=" + myElement +
           ", mySubstitutor=" + mySubstitutor +
           '}';
  }

  @NotNull
  public static GroovyResolveResult from(@NotNull PsiClassType.ClassResolveResult classResolveResult) {
    if (classResolveResult.getElement() == null) return EmptyGroovyResolveResult.INSTANCE;
    return new GroovyResolveResultImpl(
      classResolveResult.getElement(),
      null,
      null,
      classResolveResult.getSubstitutor(),
      classResolveResult.isAccessible(),
      classResolveResult.isStaticsScopeCorrect(),
      false,
      classResolveResult.isValidResult()
    );
  }
}
