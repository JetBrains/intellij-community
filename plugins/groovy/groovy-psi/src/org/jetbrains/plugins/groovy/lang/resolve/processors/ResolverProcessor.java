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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 */
public class ResolverProcessor extends GrScopeProcessorWithHints {

  private Set<String> myProcessedClasses;
  protected final PsiElement myPlace;
  private final PsiType[] myTypeArguments;

  private List<GroovyResolveResult> myCandidates;

  protected ResolverProcessor(@Nullable String name,
                              @NotNull EnumSet<ResolveKind> resolveTargets,
                              @NotNull PsiElement place,
                              @NotNull PsiType[] typeArguments) {
    super(name, resolveTargets);
    myPlace = place;
    myTypeArguments = typeArguments;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiLocalVariableImpl) { //todo a better hack
      return true; // the debugger creates a Java code block context and our expressions to evaluate resolve there
    }

    if (myResolveTargetKinds.contains(getResolveKind(element))) {
      //hack for resolve of java local vars and parameters
      //don't check field for name because they can be aliased imported
      if (element instanceof PsiVariable && !(element instanceof PsiField) &&
          getName() != null && !getName().equals(((PsiVariable)element).getName())) {
        return true;
      }
      PsiNamedElement namedElement = (PsiNamedElement)element;
      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      if (myTypeArguments.length > 0 && namedElement instanceof PsiClass) {
        substitutor = substitutor.putAll((PsiClass)namedElement, myTypeArguments);
      }

      if (namedElement instanceof PsiClass  && !(namedElement instanceof PsiTypeParameter)) {
        final PsiClass aClass = (PsiClass)namedElement;
        if (myProcessedClasses == null) myProcessedClasses = new HashSet<String>();
        if (!myProcessedClasses.add(aClass.getQualifiedName())) {
          return true;
        }
      }

      boolean isAccessible = isAccessible(namedElement);
      final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
      boolean isStaticsOK = isStaticsOK(namedElement, resolveContext, false);
      addCandidate(new GroovyResolveResultImpl(namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK));
      return !(isAccessible && isStaticsOK);
    }

    return true;
  }

  protected final void addCandidate(@NotNull GroovyResolveResult candidate) {
    PsiElement element = candidate.getElement();
    assert element == null || element.isValid() : getElementInfo(element);

    if (myCandidates == null) myCandidates = new ArrayList<GroovyResolveResult>();
    myCandidates.add(candidate);
  }

  @NotNull
  private static String getElementInfo(@NotNull PsiElement element) {
    String text;
    if (element instanceof LightElement) {
      final PsiElement context = element.getContext();
      text = context != null ? context.getText() : null;
    }
    else {
      text = element.getText();
    }
    return "invalid resolve candidate: " + element.getClass() + ", text: " + text;
  }

  @NotNull
  protected List<GroovyResolveResult> getCandidatesInternal() {
    return myCandidates == null ? Collections.<GroovyResolveResult>emptyList() : myCandidates;
  }

  protected boolean isAccessible(@NotNull PsiNamedElement namedElement) {
    if (namedElement instanceof GrField) {
      final GrField field = (GrField)namedElement;
      if (PsiUtil.isAccessible(myPlace, field)) {
        return true;
      }

      for (GrAccessorMethod method : field.getGetters()) {
        if (PsiUtil.isAccessible(myPlace, method)) {
          return true;
        }
      }
      final GrAccessorMethod setter = field.getSetter();
      if (setter != null && PsiUtil.isAccessible(myPlace, setter)) {
        return true;
      }

      return false;
    }

    return !(namedElement instanceof PsiMember) ||
           PsiUtil.isAccessible(myPlace, ((PsiMember)namedElement));
  }

  protected boolean isStaticsOK(@NotNull PsiNamedElement element, @Nullable PsiElement resolveContext, boolean filterStaticAfterInstanceQualifier) {
    if (resolveContext instanceof GrImportStatement) return true;

    if (element instanceof PsiModifierListOwner) {
      return GrStaticChecker.isStaticsOK((PsiModifierListOwner)element, myPlace, resolveContext, filterStaticAfterInstanceQualifier);
    }
    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (myCandidates == null) return GroovyResolveResult.EMPTY_ARRAY;
    return myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
  }

  public boolean hasCandidates() {
    return myCandidates != null;
  }

  @Nullable
  private static ResolveKind getResolveKind(PsiElement element) {
    if (element instanceof PsiVariable) return ResolveKind.PROPERTY;
    if (element instanceof PsiMethod) return ResolveKind.METHOD;
    if (element instanceof PsiPackage) return ResolveKind.PACKAGE;
    if (element instanceof PsiClass) return ResolveKind.CLASS;
    return null;
  }

  @Override
  public String toString() {
    return "NameHint: '" +
           getName() +
           "', " +
           myResolveTargetKinds.toString() +
           ", Candidates: " +
           (myCandidates == null ? 0 : myCandidates.size());
  }
}
