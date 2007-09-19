/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.scope.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class ResolverProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
  protected String myName;
  private EnumSet<ResolveKind> myResolveTargetKinds;
  protected PsiElement myPlace;
  protected boolean myForCompletion;
  private @NotNull PsiType[] myTypeArguments;

  protected Set<GroovyResolveResult> myCandidates = new LinkedHashSet<GroovyResolveResult>();

  public GrImportStatement getImportStatementContext() {
    return myImportStatementContext;
  }

  public void setImportStatementContext(GrImportStatement importStatementContext) {
    myImportStatementContext = importStatementContext;
  }

  protected GrImportStatement myImportStatementContext;

  public ResolverProcessor(String name, EnumSet<ResolveKind> resolveTargets,
                           PsiElement place, boolean forCompletion,
                           @NotNull PsiType[] typeArguments) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
    myPlace = place;
    myForCompletion = forCompletion;
    myTypeArguments = typeArguments;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (myResolveTargetKinds.contains(ResolveUtil.getResolveKind(element))) {
      PsiNamedElement namedElement = (PsiNamedElement) element;
      if (namedElement instanceof PsiMethod && ((PsiMethod) namedElement).isConstructor()) return true; //constructors are not interesting

      if (myTypeArguments.length > 0 && namedElement instanceof PsiClass) {
        substitutor = substitutor.putAll((PsiClass)namedElement, myTypeArguments);
      }

      boolean isAccessible = isAccessible(namedElement);
      myCandidates.add(new GroovyResolveResultImpl(namedElement, isAccessible, myImportStatementContext, substitutor));
      return myForCompletion || !isAccessible;
    }

    return true;
  }

  protected boolean isAccessible(PsiNamedElement namedElement) {
    return !(namedElement instanceof PsiMember) || PsiUtil.isAccessible((PsiMember) namedElement, myPlace, null);
  }

  public GroovyResolveResult[] getCandidates() {
    return myCandidates.toArray(GroovyResolveResult.EMPTY_ARRAY);
  }

  public <T> T getHint(Class<T> hintClass) {
    if (NameHint.class == hintClass && myName != null){
      return (T) this;
    }
    else if (ClassHint.class == hintClass) {
      return (T) this;
    }
    else if (ElementClassHint.class == hintClass) {
      return (T) this;
    }

    return null;
  }

  public void handleEvent(Event event, Object associated) {}

  public String getName() {
    return myName;
  }

  public boolean shouldProcess(ResolveKind resolveKind) {
    return myResolveTargetKinds.contains(resolveKind);
  }

  public boolean shouldProcess(Class elementClass) {
    if (PsiMethod.class.isAssignableFrom(elementClass)) return shouldProcess(ResolveKind.METHOD);
    if (PsiVariable.class.isAssignableFrom(elementClass)) return shouldProcess(ResolveKind.PROPERTY);
    if (PsiClass.class.isAssignableFrom(elementClass)) return shouldProcess(ResolveKind.CLASS_OR_PACKAGE);
    return true;
  }

  public boolean hasCandidates() {
    return myCandidates.size() > 0;
  }
}
