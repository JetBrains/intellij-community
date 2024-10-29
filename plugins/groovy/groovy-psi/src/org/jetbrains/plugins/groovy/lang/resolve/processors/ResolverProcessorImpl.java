// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT;

public class ResolverProcessorImpl extends ResolverProcessor<GroovyResolveResult> {

  private Set<String> myProcessedClasses;
  private final PsiType[] myTypeArguments;


  protected ResolverProcessorImpl(@Nullable String name,
                                  @NotNull EnumSet<DeclarationKind> resolveTargets,
                                  @NotNull PsiElement place,
                                  PsiType @NotNull [] typeArguments) {
    super(name, resolveTargets, place);
    myTypeArguments = typeArguments;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiLocalVariableImpl) { //todo a better hack
      return true; // the debugger creates a Java code block context and our expressions to evaluate resolve there
    }

    if (myResolveTargetKinds == null || myResolveTargetKinds.contains(getDeclarationKind(element))) {
      //hack for resolve of java local vars and parameters
      //don't check field for name because they can be aliased imported
      if (element instanceof PsiVariable && !(element instanceof PsiField) &&
          myName != null && !myName.equals(((PsiVariable)element).getName())) {
        return true;
      }
      PsiNamedElement namedElement = (PsiNamedElement)element;
      PsiSubstitutor substitutor = MethodResolverProcessor.getSubstitutor(state);

      if (myTypeArguments.length > 0 && namedElement instanceof PsiClass) {
        substitutor = substitutor.putAll((PsiClass)namedElement, myTypeArguments);
      }

      if (namedElement instanceof PsiClass aClass && !(namedElement instanceof PsiTypeParameter)) {
        if (myProcessedClasses == null) myProcessedClasses = new HashSet<>();
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

  private static DeclarationKind getDeclarationKind(PsiElement element) {
    if (element instanceof PsiMethod) return DeclarationKind.METHOD;
    if (element instanceof PsiEnumConstant) return DeclarationKind.ENUM_CONST;
    if (element instanceof PsiField) return DeclarationKind.FIELD;
    if (element instanceof GroovyProperty) return DeclarationKind.FIELD;
    if (element instanceof PsiVariable) return DeclarationKind.VARIABLE;
    if (element instanceof PsiClass) return DeclarationKind.CLASS;
    if (element instanceof PsiPackage) return DeclarationKind.PACKAGE;
    return null;
  }
}
