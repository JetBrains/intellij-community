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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.util.NotNullCachedComputableWrapper;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isAccessible;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isStaticsOK;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor.isAppropriatePropertyNameForGetter;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor.isAppropriatePropertyNameForSetter;

public abstract class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint {

  final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;

  private final boolean myIsLValue;

  final @Nullable PsiType myThisType;
  final @NotNull PsiType[] myTypeArguments;
  final @Nullable PsiType[] myArgumentTypes;
  private final NotNullLazyValue<SubstitutorComputer> myPropertySubstitutorComputer;
  private final NotNullLazyValue<SubstitutorComputer> myMethodSubstitutorComputer;

  final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();
  final MultiMap<GroovyResolveKind, GroovyResolveResult> myInapplicableCandidates = MultiMap.create();

  private boolean myStopExecutingMethods = false;

  GroovyResolverProcessor(@NotNull final GrReferenceExpression ref, @Nullable GrExpression myUpToArgument) {
    myRef = ref;
    myName = getReferenceName(ref);
    myAcceptableKinds = computeKinds(ref);

    myIsLValue = PsiUtil.isLValue(myRef);

    myThisType = PsiImplUtil.getQualifierType(ref);
    final PsiType[] argumentTypes = PsiUtil.getArgumentTypes(ref, false, myUpToArgument, false);
    myArgumentTypes = eraseTypes(argumentTypes);
    myTypeArguments = ref.getTypeArguments();
    myPropertySubstitutorComputer = new NotNullLazyValue<SubstitutorComputer>() {
      @NotNull
      @Override
      protected SubstitutorComputer compute() {
        return new SubstitutorComputer(myThisType, PsiType.EMPTY_ARRAY, myTypeArguments, ref, ref);
      }
    };
    myMethodSubstitutorComputer = new NotNullLazyValue<SubstitutorComputer>() {
      @NotNull
      @Override
      protected SubstitutorComputer compute() {
        return new SubstitutorComputer(myThisType, argumentTypes, myTypeArguments, ref, ref.getParent());
      }
    };
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof PsiNamedElement)) return true;
    final PsiNamedElement namedElement = (PsiNamedElement)element;

    final PsiElement resolveContext = state.get(ClassHint.RESOLVE_CONTEXT);

    final GroovyResolveKind kind = computeKindAndCheckName(namedElement, resolveContext);
    if (!myAcceptableKinds.contains(kind)) return true;

    if (kind == GroovyResolveKind.METHOD && myStopExecutingMethods) {
      return true;
    }
    else if (kind != GroovyResolveKind.PROPERTY && kind != GroovyResolveKind.METHOD) {
      if (!myCandidates.get(kind).isEmpty()) return true;
    }

    final GroovyResolveResultImpl candidate;
    {
      final PsiSubstitutor substitutor = getSubstitutor(state);
      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
      final boolean isAccessible = isAccessible(myRef, namedElement);
      final boolean isStaticsOK = isStaticsOK(myRef, namedElement, resolveContext, false);

      if (kind == GroovyResolveKind.METHOD || kind == GroovyResolveKind.PROPERTY) {
        final PsiMethod method = (PsiMethod)namedElement;
        final boolean isApplicable = kind == GroovyResolveKind.PROPERTY || isApplicable(myArgumentTypes, method, null, myRef, true);

        final NotNullComputable<PsiSubstitutor> substitutorComputer;
        if (kind == GroovyResolveKind.METHOD) {
          substitutorComputer = new NotNullCachedComputableWrapper<PsiSubstitutor>(new NotNullComputable<PsiSubstitutor>() {
            @NotNull
            @Override
            public PsiSubstitutor compute() {
              return myMethodSubstitutorComputer.getValue().obtainSubstitutor(substitutor, method, resolveContext);
            }
          });
        }
        else {
          substitutorComputer = new NotNullCachedComputableWrapper<PsiSubstitutor>(new NotNullComputable<PsiSubstitutor>() {
            @NotNull
            @Override
            public PsiSubstitutor compute() {
              return myPropertySubstitutorComputer.getValue().obtainSubstitutor(substitutor, method, resolveContext);
            }
          });
        }
        candidate = new GroovyMethodResult(
          method, resolveContext, spreadState,
          substitutor, substitutorComputer,
          kind == GroovyResolveKind.PROPERTY,
          isAccessible, isStaticsOK, isApplicable
        );
      }
      else {
        candidate = new GroovyResolveResultImpl(
          namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK, false, true
        );
      }
    }
    (candidate.isValidResult() ? myCandidates : myInapplicableCandidates).putValue(kind, candidate);
    return true;
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
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && !myCandidates.get(GroovyResolveKind.METHOD).isEmpty()) {
      myStopExecutingMethods = true;
    }
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    for (GroovyResolveKind resolveKind : myAcceptableKinds) {
      if (resolveKind.declarationKinds.contains(kind)) return true;
    }
    return false;
  }

  @NotNull
  public abstract List<GroovyResolveResult> getCandidates();

  public final GroovyResolveResult[] getCandidatesArray() {
    final List<GroovyResolveResult> candidates = getCandidates();
    final int size = candidates.size();
    if (size == 0) return GroovyResolveResult.EMPTY_ARRAY;
    if (size == 1) return new GroovyResolveResult[]{candidates.get(0)};
    return candidates.toArray(new GroovyResolveResult[size]);
  }

  private GroovyResolveKind computeKindAndCheckName(PsiNamedElement element, PsiElement resolveContext) {
    final String importedName = resolveContext instanceof GrImportStatement ? ((GrImportStatement)resolveContext).getImportedName() : null;
    if (element instanceof PsiMethod) {
      if (importedName == null) {
        if (myIsLValue) {
          if (GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, myName)) {
            return GroovyResolveKind.PROPERTY;
          }
        }
        else {
          if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, myName)) {
            return GroovyResolveKind.PROPERTY;
          }
        }
      }
      else {
        if (myIsLValue) {
          if (GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, null) &&
              (isAppropriatePropertyNameForSetter(importedName, myName) || myName.equals(importedName))) {
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

        // import static Foo.foo; setFoo(1) or getFoo()
        if (importedName.equals(GroovyPropertyUtils.getPropertyNameByGetterName(myName, true)) ||
            importedName.equals(GroovyPropertyUtils.getPropertyNameBySetterName(myName))) {
          return GroovyResolveKind.METHOD;
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
        else if (element instanceof GrBindingVariable) {
          return GroovyResolveKind.BINDING;
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

  @NotNull
  protected Pair<Boolean, List<GroovyResolveResult>> getCandidates(@NotNull GroovyResolveKind... kinds) {
    final List<GroovyResolveResult> results = ContainerUtil.newSmartList();
    for (GroovyResolveKind kind : kinds) {
      results.addAll(myCandidates.get(kind));
    }
    return !results.isEmpty() ? Pair.create(true, results) : Pair.create(false, (List<GroovyResolveResult>)null);
  }

  @NotNull
  protected static PsiSubstitutor getSubstitutor(@NotNull final ResolveState state) {
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    return substitutor;
  }

  @NotNull
  private static String getReferenceName(@NotNull GrReferenceExpression ref) {
    final String name = ref.getReferenceName();
    assert name != null : "Reference name cannot be null";
    return name;
  }

  @NotNull
  private static EnumSet<GroovyResolveKind> computeKinds(@NotNull GrReferenceExpression ref) {
    if (ref.hasAt()) return EnumSet.of(GroovyResolveKind.FIELD);
    if (ref.hasMemberPointer()) return EnumSet.of(GroovyResolveKind.METHOD);

    final EnumSet<GroovyResolveKind> result = EnumSet.allOf(GroovyResolveKind.class);

    if (!ResolveUtil.canBeClass(ref)) result.remove(GroovyResolveKind.CLASS);
    if (!ResolveUtil.canBePackage(ref)) result.remove(GroovyResolveKind.PACKAGE);
    if (ref.isQualified()) result.removeAll(EnumSet.of(GroovyResolveKind.VARIABLE, GroovyResolveKind.BINDING));
    if (!(ref.getParent() instanceof GrMethodCall)) result.remove(GroovyResolveKind.METHOD);

    return result;
  }

  @Nullable
  private static PsiType[] eraseTypes(@Nullable PsiType[] types) {
    final PsiType[] erasedTypes = types == null ? null : Arrays.copyOf(types, types.length);
    if (erasedTypes != null) {
      for (int i = 0; i < types.length; i++) {
        erasedTypes[i] = TypeConversionUtil.erasure(erasedTypes[i]);
      }
    }
    return erasedTypes;
  }
}
