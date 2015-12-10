/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collection;
import java.util.EnumSet;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isAccessible;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isStaticsOK;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor.isAppropriatePropertyNameForGetter;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor.isAppropriatePropertyNameForSetter;

public class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint {

  private final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  private final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;
  private final boolean myByShape;

  private final @NotNull PsiType[] myTypeArguments;

  private final NullableLazyValue<PsiType> myThisType = new NullableLazyValue<PsiType>() {
    @Nullable
    @Override
    protected PsiType compute() {
      return PsiImplUtil.getQualifierType(myRef);
    }
  };
  private final NullableLazyValue<SubstitutorComputer> mySubstitutorComputer = new NullableLazyValue<SubstitutorComputer>() {
    @Nullable
    @Override
    protected SubstitutorComputer compute() {
      return myByShape
             ? null
             : new SubstitutorComputer(myThisType.getValue(), PsiType.EMPTY_ARRAY, myTypeArguments, myRef, myRef);
    }
  };
  private final NotNullLazyValue<Boolean> myIsLValue = new NotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return PsiUtil.isLValue(myRef);
    }
  };
  private final boolean myIsPartOfFqn;

  private final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();
  private final MultiMap<GroovyResolveKind, GroovyResolveResult> myInapplicableCandidates = MultiMap.create();

  public GroovyResolverProcessor(@NotNull GrReferenceExpression ref, @NotNull String name, boolean byShape) {
    myRef = ref;
    myName = name;
    myAcceptableKinds = computeKinds(myRef);
    myByShape = byShape;
    myTypeArguments = myRef.getTypeArguments();
    myIsPartOfFqn = ResolveUtil.isPartOfFQN(myRef);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof PsiNamedElement)) return true;
    final PsiNamedElement namedElement = (PsiNamedElement)element;

    final PsiElement resolveContext = state.get(ClassHint.RESOLVE_CONTEXT);

    final GroovyResolveKind kind = computeKindAndCheckName(namedElement, resolveContext);
    if (!myAcceptableKinds.contains(kind)) return true;

    if (kind != GroovyResolveKind.PROPERTY && kind != GroovyResolveKind.METHOD) {
      if (!myCandidates.get(kind).isEmpty()) return true;
    }

    final GroovyResolveResultImpl candidate;
    {
      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      if (kind == GroovyResolveKind.PROPERTY && mySubstitutorComputer.getValue() != null) {
        substitutor = mySubstitutorComputer.getValue().obtainSubstitutor(substitutor, (PsiMethod)element, resolveContext);
      }

      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
      final boolean isAccessible = isAccessible(myRef, namedElement);
      final boolean isStaticsOK = isStaticsOK(myRef, namedElement, resolveContext, false);

      candidate = new GroovyResolveResultImpl(
        namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK, kind == GroovyResolveKind.PROPERTY, true
      );
    }
    (candidate.isValidResult() ? myCandidates : myInapplicableCandidates).putValue(kind, candidate);
    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    Pair<Boolean, GroovyResolveResult[]> candidates;

    // we do not care about other stuff if we have field operator (.@)
    if (myRef.hasAt()) {
      candidates = getCandidates(GroovyResolveKind.FIELD);
      return candidates.first ? candidates.second : GroovyResolveResult.EMPTY_ARRAY;
    }

    // return package if whole ref text is valid class name
    if (myAcceptableKinds.contains(GroovyResolveKind.PACKAGE) && myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE);
      if (candidates.first) {
        final GroovyResolveResult candidate = candidates.second[0];
        final PsiElement element = candidate.getElement();
        assert element instanceof PsiPackage;
        final GrReferenceExpressionImpl topRef = getContextReferenceExpression(myRef);
        if (topRef != null) {
          final String fqn = topRef.getTextSkipWhiteSpaceAndComments();
          if (JavaPsiFacade.getInstance(myRef.getProject()).findClass(fqn, myRef.getResolveScope()) != null) {
            return candidates.second;
          }
        }
      }
    }

    candidates = getCandidates(GroovyResolveKind.VARIABLE);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (candidates.first) {
      assert candidates.second.length == 1;
      final GroovyResolveResult candidate = candidates.second[0];
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiUtil.getContextClass(myRef) == containingClass) return candidates.second;
      }
      else if (!(element instanceof GrBindingVariable)) {
        return candidates.second;
      }
    }

    if (myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
      if (candidates.first) {
        return candidates.second;
      }
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (candidates.first) {
      final GroovyResolveResult[] results = candidates.second;
      return results.length <= 1 ? results : new GroovyResolveResult[]{results[0]};
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (candidates.first) {
      return candidates.second;
    }

    for (GroovyResolveKind kind : myAcceptableKinds) {
      Collection<GroovyResolveResult> results = myInapplicableCandidates.get(kind);
      if (!results.isEmpty()) return results.toArray(new GroovyResolveResult[results.size()]);
    }

    return GroovyResolveResult.EMPTY_ARRAY;
  }

  @NotNull
  private Pair<Boolean, GroovyResolveResult[]> getCandidates(@NotNull GroovyResolveKind... kinds) {
    final Collection<GroovyResolveResult> results = ContainerUtil.newSmartList();
    for (GroovyResolveKind kind : kinds) {
      results.addAll(myCandidates.get(kind));
    }
    return !results.isEmpty() ? Pair.create(true, results.toArray(new GroovyResolveResult[results.size()]))
                              : Pair.create(false, (GroovyResolveResult[])null);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    return null;
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    for (GroovyResolveKind resolveKind : myAcceptableKinds) {
      if (resolveKind.declarationKinds.contains(kind)) return true;
    }
    return false;
  }

  private GroovyResolveKind computeKindAndCheckName(PsiNamedElement element, PsiElement resolveContext) {
    final String importedName = resolveContext instanceof GrImportStatement ? ((GrImportStatement)resolveContext).getImportedName() : null;
    if (element instanceof PsiMethod) {
      if (myIsLValue.getValue()) {
        if ((importedName != null &&
             GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, null) &&
             (isAppropriatePropertyNameForSetter(importedName, myName) || myName.equals(importedName)) ||
             importedName == null && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, myName))) {
          return GroovyResolveKind.PROPERTY;
        }
      }
      else {
        if (importedName == null) {
          if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, myName)) {
            return GroovyResolveKind.PROPERTY;
          }
        }
        else {
          if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, null) &&
              (isAppropriatePropertyNameForGetter((PsiMethod)element, importedName, myName) ||
               myName.equals(importedName))) {
            return GroovyResolveKind.PROPERTY;
          }
        }
      }
    }
    if (importedName == null) {
      if (myName.equals(element.getName())) {
        if (element instanceof PsiClass) {
          return GroovyResolveKind.CLASS;
        }
        else if (element instanceof PsiPackage) {
          return GroovyResolveKind.PACKAGE;
        }
        if (element instanceof PsiMethod) {
          return GroovyResolveKind.METHOD;
        }
        else if (element instanceof PsiField) {
          return GroovyResolveKind.FIELD;
        }
        else if (element instanceof PsiVariable) {
          return GroovyResolveKind.VARIABLE;
        }
      }
    }
    else {
      if (myName.equals(importedName)) {
        if (element instanceof PsiClass) {
          return GroovyResolveKind.CLASS;
        }
        if (element instanceof PsiMethod) {
          return GroovyResolveKind.METHOD;
        }
        else if (element instanceof PsiField) {
          return GroovyResolveKind.FIELD;
        }
      }
    }
    return null;
  }

  private static EnumSet<GroovyResolveKind> computeKinds(GrReferenceExpression ref) {
    if (ref.hasAt()) return EnumSet.of(GroovyResolveKind.FIELD);
    if (ref.hasMemberPointer()) return EnumSet.of(GroovyResolveKind.METHOD);

    final EnumSet<GroovyResolveKind> result = EnumSet.allOf(GroovyResolveKind.class);

    if (!ResolveUtil.canBeClass(ref)) result.remove(GroovyResolveKind.CLASS);
    if (!ResolveUtil.canBePackage(ref)) result.remove(GroovyResolveKind.PACKAGE);
    if (ref.isQualified()) result.remove(GroovyResolveKind.VARIABLE);
    if (!(ref.getParent() instanceof GrMethodCall)) result.remove(GroovyResolveKind.METHOD);

    return result;
  }

  private static GrReferenceExpressionImpl getContextReferenceExpression(GrReferenceExpression ref) {
    final PsiElement firstNonReferenceExprParent = PsiTreeUtil.skipParentsOfType(ref, GrReferenceExpressionImpl.class);
    return (GrReferenceExpressionImpl)PsiTreeUtil.findFirstParent(ref, new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement parent) {
        return parent.getParent() == firstNonReferenceExprParent && parent instanceof GrReferenceExpressionImpl;
      }
    });
  }
}
