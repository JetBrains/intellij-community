// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT;

/**
 * @author Maxim.Medvedev
 * @deprecated use {@link PropertyProcessor}
 */
@Deprecated
public class AccessorResolverProcessor extends MethodResolverProcessor {
  private final String myPropertyName;
  private final boolean mySearchForGetter;
  private final SubstitutorComputer mySubstitutorComputer;


  public AccessorResolverProcessor(@Nullable String accessorName, @NotNull String propertyName, @NotNull PsiElement place, boolean searchForGetter) {
    this(accessorName, propertyName, place, searchForGetter, null, PsiType.EMPTY_ARRAY);
  }

  public AccessorResolverProcessor(@Nullable String accessorName,
                                   @NotNull String propertyName,
                                   @NotNull PsiElement place,
                                   boolean searchForGetter,
                                   @Nullable PsiType thisType,
                                   @NotNull PsiType[] typeArguments) {
    super(accessorName, place, false, thisType, null, typeArguments, false);
    myPropertyName = propertyName;

    mySearchForGetter = searchForGetter;
    mySubstitutorComputer = new SubstitutorComputer(thisType, PsiType.EMPTY_ARRAY, typeArguments, place, myPlace);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return !checkAccessor(element, state, myPropertyName, mySearchForGetter) || addAccessor((PsiMethod)element, state);
  }

  static boolean checkAccessor(@NotNull PsiElement element, @NotNull ResolveState state, @NotNull String myPropertyName, boolean mySearchForGetter) {
    final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
    String importedName = resolveContext instanceof GrImportStatement ? ((GrImportStatement)resolveContext).getImportedName() : null;
    if (mySearchForGetter) {
      if (element instanceof PsiMethod &&
          (importedName != null && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, null) &&
           (isAppropriatePropertyNameForGetter((PsiMethod)element, importedName, myPropertyName) || myPropertyName.equals(importedName)) ||
           importedName == null && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, myPropertyName))) {
        return true;
      }
    }
    else {
      if (element instanceof PsiMethod &&
          (importedName != null && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, null) &&
           (isAppropriatePropertyNameForSetter(importedName, myPropertyName) || myPropertyName.equals(importedName)) ||
           importedName == null && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, myPropertyName))) {
        return true;
      }
    }
    return false;
  }

  /**
   * use only for imported properties
   */
  public static boolean isAppropriatePropertyNameForSetter(@NotNull String importedName, @NotNull String propertyName) {
    propertyName = GroovyPropertyUtils.decapitalize(propertyName);
    return propertyName.equals(GroovyPropertyUtils.getPropertyNameBySetterName(importedName));
  }

  /**
   * use only for imported properties
   */
  public static boolean isAppropriatePropertyNameForGetter(@NotNull PsiMethod getter, @NotNull String importedNameForGetter, @NotNull String propertyName) {
    propertyName = GroovyPropertyUtils.decapitalize(propertyName);
    return propertyName.equals(getPropertyNameByGetter(getter, importedNameForGetter));
  }

  @Nullable
  private static String getPropertyNameByGetter(PsiMethod element, String importedName) {
    return GroovyPropertyUtils.getPropertyNameByGetterName(importedName, isBoolean(element));
  }

  private static boolean isBoolean(PsiMethod method) {
    return PsiType.BOOLEAN.equals(method.getReturnType());
  }

  private boolean addAccessor(final PsiMethod method, ResolveState state) {
    final PsiSubstitutor substitutor = getSubstitutor(state);

    final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
    final NotNullComputable<PsiSubstitutor> substitutorComputer =
      () -> mySubstitutorComputer.obtainSubstitutor(substitutor, method, resolveContext);
    boolean isAccessible = isAccessible(method);
    final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
    boolean isStaticsOK = isStaticsOK(method, resolveContext, false);
    final GroovyMethodResultImpl candidate = new GroovyMethodResultImpl(
      method, resolveContext, spreadState, substitutor, substitutorComputer,  isAccessible, isStaticsOK
    );
    if (isAccessible && isStaticsOK) {
      addCandidate(candidate);
      return method instanceof GrGdkMethod; //don't stop searching if we found only gdk method
    }
    else {
      addInapplicableCandidate(candidate);
      return true;
    }
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCandidates() {
    final boolean hasApplicableCandidates = hasApplicableCandidates();
    final GroovyResolveResult[] candidates = super.getCandidates();
    if (hasApplicableCandidates) {
      if (candidates.length <= 1) return candidates;
      return new GroovyResolveResult[]{candidates[0]};
    }
    else {
      return candidates;
    }
  }
}
