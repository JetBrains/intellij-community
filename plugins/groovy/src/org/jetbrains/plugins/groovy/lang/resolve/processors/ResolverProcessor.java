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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

/**
 * @author ven
 */
public class ResolverProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
  public static final Key<GroovyPsiElement> RESOLVE_CONTEXT = Key.create("RESOLVE_CONTEXT");

  public static final EnumSet<ResolveKind> RESOLVE_KINDS_CLASS_PACKAGE = EnumSet.of(CLASS, PACKAGE);
  public static final EnumSet<ResolveKind> RESOLVE_KINDS_CLASS = EnumSet.of(CLASS);
  public static final EnumSet<ResolveKind> RESOLVE_KINDS_METHOD = EnumSet.of(METHOD);
  public static final EnumSet<ResolveKind> RESOLVE_KINDS_METHOD_PROPERTY = EnumSet.of(METHOD, PROPERTY);
  public static final EnumSet<ResolveKind> RESOLVE_KINDS_PROPERTY = EnumSet.of(PROPERTY);
  
  protected String myName;
  private final EnumSet<ResolveKind> myResolveTargetKinds;
  private Set<String> myProcessedClasses;
  protected PsiElement myPlace;
  private
  @NotNull final PsiType[] myTypeArguments;

  private Set<GroovyResolveResult> myCandidates;

  protected ResolverProcessor(String name, EnumSet<ResolveKind> resolveTargets,
                              PsiElement place,
                              @NotNull PsiType[] typeArguments) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
    myPlace = place;
    myTypeArguments = typeArguments;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myResolveTargetKinds.contains(getResolveKind(element))) {
      PsiNamedElement namedElement = (PsiNamedElement) element;
      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      if (myTypeArguments.length > 0 && namedElement instanceof PsiClass) {
        substitutor = substitutor.putAll((PsiClass) namedElement, myTypeArguments);
      }

      if (namedElement instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)namedElement;
        if (myProcessedClasses == null) myProcessedClasses = new HashSet<String>();
        if (!myProcessedClasses.add(aClass.getQualifiedName())) {
          return true;
        }
      }

      boolean isAccessible = isAccessible(namedElement);
      final GroovyPsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      boolean isStaticsOK = isStaticsOK(namedElement, resolveContext);
      addCandidate(new GroovyResolveResultImpl(namedElement, resolveContext, substitutor, isAccessible, isStaticsOK));
      return !isAccessible;
    }

    return true;
  }

  protected final void addCandidate(GroovyResolveResult candidate) {
    if (myCandidates == null) myCandidates = new LinkedHashSet<GroovyResolveResult>();
    myCandidates.add(candidate);
  }

  protected Set<GroovyResolveResult> getCandidatesInternal() {
    return myCandidates == null ? Collections.<GroovyResolveResult>emptySet() : myCandidates;
  }

  protected boolean isAccessible(PsiNamedElement namedElement) {
    return !(namedElement instanceof PsiMember) ||
        PsiUtil.isAccessible(myPlace, ((PsiMember) namedElement));
  }

  protected boolean isStaticsOK(PsiNamedElement element, GroovyPsiElement resolveContext) {
    if (resolveContext instanceof GrImportStatement) return true;

    if (element instanceof PsiModifierListOwner) {
      return PsiUtil.isStaticsOK((PsiModifierListOwner) element, myPlace);
    }
    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (myCandidates == null) return GroovyResolveResult.EMPTY_ARRAY;
    return myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getHint(Key<T> hintKey) {
    if ((NameHint.KEY == hintKey && myName != null) || ClassHint.KEY == hintKey || ElementClassHint.KEY == hintKey) {
      return (T) this;
    }

    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  public String getName() {
    return myName;
  }

  public boolean shouldProcess(ResolveKind resolveKind) {
    return myResolveTargetKinds.contains(resolveKind);
  }

  public boolean shouldProcess(DeclaractionKind kind) {
    switch (kind) {
      case CLASS:
        return shouldProcess(CLASS);

      case ENUM_CONST:
      case VARIABLE:
      case FIELD:
        return shouldProcess(PROPERTY);

      case METHOD:
        return shouldProcess(METHOD);

      case PACKAGE:
        return shouldProcess(PACKAGE);
    }

    return false;
  }

  public boolean hasCandidates() {
    return myCandidates != null;
  }

  private static ResolveKind getResolveKind(PsiElement element) {
    if (element instanceof PsiVariable) return PROPERTY;
    if (element instanceof GrReferenceExpression) return PROPERTY;
    if (element instanceof PsiMethod) return METHOD;
    if (element instanceof PsiPackage) return PACKAGE;

    return CLASS;
  }

  public String getName(ResolveState state) {
    //todo[DIANA] implement me!
    return myName;
  }

  @Override
  public String toString() {
    return "NameHint: '" + myName + "', " + myResolveTargetKinds.toString() + ", Candidates: " + (myCandidates == null ? 0 : myCandidates.size());
  }
}
