/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

class GroovyResolverProcessorImpl extends GroovyResolverProcessor implements GrMethodComparator.Context {

  private final boolean myIsPartOfFqn;

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds) {
    super(ref, kinds, null);
    myIsPartOfFqn = ResolveUtil.isPartOfFQN(ref);
  }

  @NotNull
  public List<GroovyResolveResult> getCandidates() {
    List<GroovyResolveResult> candidates;

    // return package if whole ref text is valid class name
    if (myAcceptableKinds.contains(GroovyResolveKind.PACKAGE) && myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE);
      if (!candidates.isEmpty()) {
        final GroovyResolveResult candidate = candidates.get(0);
        final PsiElement element = candidate.getElement();
        assert element instanceof PsiPackage;
        final GrReferenceExpressionImpl topRef = getContextReferenceExpression(myRef);
        if (topRef != null) {
          final String fqn = topRef.getTextSkipWhiteSpaceAndComments();
          if (JavaPsiFacade.getInstance(myRef.getProject()).findClass(fqn, myRef.getResolveScope()) != null) {
            return candidates;
          }
        }
      }
    }

    candidates = getCandidates(GroovyResolveKind.VARIABLE);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.METHOD);
    if (!candidates.isEmpty()) {
      final List<GroovyResolveResult> results = filterMethodCandidates(candidates);
      return myRef.hasMemberPointer() ? collapseReflectedMethods(results) : results;
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (!candidates.isEmpty()) {
      assert candidates.size() == 1;
      final GroovyResolveResult candidate = candidates.get(0);
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiUtil.getContextClass(myRef) == containingClass) return candidates;
      }
      else if (!(element instanceof GrBindingVariable)) {
        return candidates;
      }
    }

    if (myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
      if (!candidates.isEmpty()) {
        return candidates;
      }
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (!candidates.isEmpty()) {
      return candidates.size() <= 1 ? candidates : ContainerUtil.newSmartList(candidates.get(0));
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.BINDING);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    for (GroovyResolveKind kind : myAcceptableKinds) {
      Collection<GroovyResolveResult> results = myInapplicableCandidates.get(kind);
      if (!results.isEmpty()) {
        return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(
          filterCorrectParameterCount(results)
        ));
      }
    }

    return Collections.emptyList();
  }

  private static GrReferenceExpressionImpl getContextReferenceExpression(GrReferenceExpression ref) {
    final PsiElement firstNonReferenceExprParent = PsiTreeUtil.skipParentsOfType(ref, GrReferenceExpressionImpl.class);
    return (GrReferenceExpressionImpl)PsiTreeUtil.findFirstParent(
      ref, parent -> parent.getParent() == firstNonReferenceExprParent && parent instanceof GrReferenceExpressionImpl
    );
  }

  private List<GroovyResolveResult> filterCorrectParameterCount(Collection<GroovyResolveResult> candidates) {
    if (myArgumentTypes == null) return ContainerUtil.newArrayList(candidates);
    final List<GroovyResolveResult> result = ContainerUtil.newSmartList();
    for (GroovyResolveResult candidate : candidates) {
      if (candidate instanceof GroovyMethodResult) {
        if (((GroovyMethodResult)candidate).getElement().getParameterList().getParametersCount() == myArgumentTypes.length) {
          result.add(candidate);
        }
      }
      else {
        result.add(candidate);
      }
    }
    if (!result.isEmpty()) return result;
    return ContainerUtil.newArrayList(candidates);
  }

  private List<GroovyResolveResult> filterMethodCandidates(List<GroovyResolveResult> candidates) {
    if (candidates.size() <= 1) return candidates;

    final List<GroovyResolveResult> results = ContainerUtil.newArrayList();
    final Iterator<GroovyResolveResult> itr = candidates.iterator();

    results.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      final GroovyResolveResult resolveResult = itr.next();
      if (resolveResult instanceof GroovyMethodResult) {
        for (Iterator<GroovyResolveResult> iterator = results.iterator(); iterator.hasNext(); ) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          if (otherResolveResult instanceof GroovyMethodResult) {
            int res = GrMethodComparator.compareMethods((GroovyMethodResult)resolveResult, (GroovyMethodResult)otherResolveResult, this);
            if (res > 0) {
              continue Outer;
            }
            else if (res < 0) {
              iterator.remove();
            }
          }
        }
      }

      results.add(resolveResult);
    }

    return results;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @NotNull
  @Override
  public PsiElement getPlace() {
    return myRef;
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  private static List<GroovyResolveResult> collapseReflectedMethods(Collection<GroovyResolveResult> candidates) {
    Set<GrMethod> visited = ContainerUtil.newHashSet();
    List<GroovyResolveResult> collapsed = ContainerUtil.newArrayList();
    for (GroovyResolveResult result : candidates) {
      PsiElement element = result.getElement();
      if (element instanceof GrReflectedMethod) {
        GrMethod baseMethod = ((GrReflectedMethod)element).getBaseMethod();
        if (visited.add(baseMethod)) {
          collapsed.add(PsiImplUtil.reflectedToBase(result, baseMethod, (GrReflectedMethod)element));
        }
      }
      else {
        collapsed.add(result);
      }
    }
    return collapsed;
  }
}
