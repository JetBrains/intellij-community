// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.search.RelaxedDirectInheritorChecker;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GroovyDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  /**
   * During inheritance checks across Groovy classes, some ast transformations might be applied to them. These transformations, in turn
   * may call inheritance checks again, so endless recursion is created. Put this key into the base class to skip inheritance checks.
   *
   * @see ClassInheritorsSearch.SearchParameters#isCheckInheritance()
   * @see DirectClassInheritorsSearch.SearchParameters#isCheckInheritance()
   * @see org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
   */
  public static final Key<Boolean> IGNORE_INHERITANCE_CHECK = Key.create("IGNORE_CHECK_INHERITANCE");

  private static @NotNull List<PsiClass> getDerivingClassCandidates(PsiClass clazz, GlobalSearchScope scope, boolean includeAnonymous) {
    final String name = clazz.getName();
    if (name == null) return Collections.emptyList();
    final ArrayList<PsiClass> inheritors = new ArrayList<>();
    for (GrReferenceList list : StubIndex.getElements(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope,
                                                      GrReferenceList.class)) {
      final PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition) {
        inheritors.add((PsiClass)parent);
      }
    }
    if (includeAnonymous) {
      inheritors.addAll(StubIndex.getElements(GrAnonymousClassIndex.KEY, name, clazz.getProject(), scope, GrAnonymousClassDefinition.class));
    }
    return inheritors;
  }

  @Override
  public boolean execute(final @NotNull DirectClassInheritorsSearch.SearchParameters queryParameters, final @NotNull Processor<? super PsiClass> consumer) {
    final PsiClass clazz = queryParameters.getClassToProcess();
    SearchScope scope = ReadAction.compute(() -> queryParameters.getScope().intersectWith(clazz.getUseScope()));
    Project project = PsiUtilCore.getProjectInReadAction(clazz);
    GlobalSearchScope globalSearchScope = GlobalSearchScopeUtil.toGlobalSearchScope(scope, project);
    DumbService dumbService = DumbService.getInstance(project);
    List<PsiClass> candidates = dumbService.runReadActionInSmartMode(() -> {
      if (!clazz.isValid()) return Collections.emptyList();
      return getDerivingClassCandidates(clazz, globalSearchScope, queryParameters.includeAnonymous());
    });

    if (!candidates.isEmpty()) {
      RelaxedDirectInheritorChecker checker = dumbService.runReadActionInSmartMode(() -> new RelaxedDirectInheritorChecker(clazz));
      boolean shouldSkipInheritanceChecks = shouldSkipInheritanceChecks(queryParameters);
      for (PsiClass candidate : candidates) {
        if (shouldSkipInheritanceChecks || (!queryParameters.isCheckInheritance() || dumbService.runReadActionInSmartMode(() -> checker.checkInheritance(candidate)))) {
          if (!consumer.process(candidate)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean shouldSkipInheritanceChecks(@NotNull DirectClassInheritorsSearch.SearchParameters queryParameters) {
    if (Boolean.TRUE.equals(queryParameters.getClassToProcess().getUserData(IGNORE_INHERITANCE_CHECK))) return true;

    ClassInheritorsSearch.SearchParameters originalParameters = queryParameters.getOriginalParameters();
    if (originalParameters == null) return false;

    PsiClass baseClass = originalParameters.getClassToProcess();
    return Boolean.TRUE.equals(baseClass.getUserData(IGNORE_INHERITANCE_CHECK));
  }
}
