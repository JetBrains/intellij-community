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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.NotNullCachedComputableWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isAccessible;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isStaticsOK;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorResolverProcessor.*;

public abstract class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint, NameHint {

  protected final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  protected final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;

  private final boolean myIsLValue;

  protected final @Nullable PsiType myThisType;
  protected final @NotNull PsiType[] myTypeArguments;
  private final @Nullable PsiType[] myArgumentTypesNonErased;
  protected final @Nullable PsiType[] myArgumentTypes;

  private final NotNullLazyValue<SubstitutorComputer> myPropertySubstitutorComputer = new NotNullLazyValue<SubstitutorComputer>() {
    @NotNull
    @Override
    protected SubstitutorComputer compute() {
      return new SubstitutorComputer(myThisType, PsiType.EMPTY_ARRAY, myTypeArguments, myRef, myRef);
    }
  };
  private final NotNullLazyValue<SubstitutorComputer> myMethodSubstitutorComputer = new NotNullLazyValue<SubstitutorComputer>() {
    @NotNull
    @Override
    protected SubstitutorComputer compute() {
      return new SubstitutorComputer(myThisType, myArgumentTypesNonErased, myTypeArguments, myRef, myRef.getParent());
    }
  };
  private final List<PsiScopeProcessor> myAccessorProcessors;

  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();
  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myInapplicableCandidates = MultiMap.create();

  private boolean myStopExecutingMethods = false;

  GroovyResolverProcessor(@NotNull GrReferenceExpression ref,
                          @NotNull EnumSet<GroovyResolveKind> kinds,
                          @Nullable GrExpression myUpToArgument) {
    myRef = ref;
    myAcceptableKinds = kinds;
    myName = getReferenceName(ref);

    myIsLValue = PsiUtil.isLValue(myRef);

    myThisType = PsiImplUtil.getQualifierType(ref);
    myTypeArguments = ref.getTypeArguments();
    if (kinds.contains(GroovyResolveKind.METHOD)) {
      myArgumentTypesNonErased = PsiUtil.getArgumentTypes(ref, false, myUpToArgument, false);
      myArgumentTypes = eraseTypes(myArgumentTypesNonErased);
    }
    else {
      myArgumentTypes = myArgumentTypesNonErased = null;
    }

    myAccessorProcessors = calcAccessorProcessors();
  }

  private List<PsiScopeProcessor> calcAccessorProcessors() {
    if (isPropertyResolve()) {
      if (myIsLValue) {
        return Collections.singletonList(accessorProcessor(GroovyPropertyUtils.getSetterName(myName)));
      }
      return ContainerUtil.newArrayList(
        accessorProcessor(GroovyPropertyUtils.getGetterNameNonBoolean(myName)),
        accessorProcessor(GroovyPropertyUtils.getGetterNameBoolean(myName))
      );
    }
    return Collections.emptyList();
  }

  private GrScopeProcessorWithHints accessorProcessor(@NotNull final String name) {
    return new GrScopeProcessorWithHints(name, GroovyResolveKind.METHOD.declarationKinds) {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        return !checkAccessor(element, state, GroovyResolverProcessor.this.myName, !myIsLValue) ||
               GroovyResolverProcessor.this.execute(element, state);
      }
    };
  }

  public boolean isPropertyResolve() {
    return myAcceptableKinds.contains(GroovyResolveKind.PROPERTY);
  }

  public static List<PsiScopeProcessor> allProcessors(PsiScopeProcessor processor) {
    if (processor instanceof GroovyResolverProcessor && !((GroovyResolverProcessor)processor).myStopExecutingMethods) {
      List<PsiScopeProcessor> accessors = ((GroovyResolverProcessor)processor).myAccessorProcessors;
      if (!accessors.isEmpty()) {
        return ContainerUtil.concat(Collections.singletonList(processor), accessors);
      }
    }
    return Collections.singletonList(processor);
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
          substitutorComputer = new NotNullCachedComputableWrapper<>(
            () -> myMethodSubstitutorComputer.getValue().obtainSubstitutor(substitutor, method, resolveContext)
          );
        }
        else {
          substitutorComputer = new NotNullCachedComputableWrapper<>(
            () -> myPropertySubstitutorComputer.getValue().obtainSubstitutor(substitutor, method, resolveContext)
          );
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

    if (candidate.isValidResult() && kind == GroovyResolveKind.VARIABLE) {
      myStopExecutingMethods = true;
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY || hintKey == NameHint.KEY) {
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
    if (kind == DeclarationKind.METHOD) {
      if (myStopExecutingMethods) return false;
      if (isPropertyResolve() && !myAcceptableKinds.contains(GroovyResolveKind.METHOD)) return false;
    }
    for (GroovyResolveKind resolveKind : myAcceptableKinds) {
      if (resolveKind.declarationKinds.contains(kind)) return true;
    }
    return false;
  }

  @NotNull
  @Override
  public String getName(@NotNull ResolveState state) {
    return myName;
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
  protected List<GroovyResolveResult> getCandidates(@NotNull GroovyResolveKind... kinds) {
    final List<GroovyResolveResult> results = ContainerUtil.newSmartList();
    for (GroovyResolveKind kind : kinds) {
      results.addAll(myCandidates.get(kind));
    }
    return results;
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
