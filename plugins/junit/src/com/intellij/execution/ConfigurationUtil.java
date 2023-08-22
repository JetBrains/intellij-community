// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class ConfigurationUtil {
  // return true if there is JUnit4 test
  public static boolean findAllTestClasses(@NotNull final TestClassFilter testClassFilter,
                                           @Nullable final Module module,
                                           @NotNull final Set<? super PsiClass> found) {
    final PsiManager manager = testClassFilter.getPsiManager();

    final Project project = manager.getProject();
    GlobalSearchScope projectScopeWithoutLibraries = GlobalSearchScope.projectScope(project);
    final GlobalSearchScope scope = projectScopeWithoutLibraries.intersectWith(testClassFilter.getScope());

    final PsiClass base = testClassFilter.getBase();
    if (base != null) {
      ClassInheritorsSearch.search(base, scope, true, true, false).forEach(new ReadActionProcessor<>() {
        @Override
        public boolean processInReadAction(PsiClass aClass) {
          if (testClassFilter.isAccepted(aClass)) found.add(aClass);
          return true;
        }
      });
    }

    // classes having suite() method
    final PsiMethod[] suiteMethods =
      ReadAction.compute(() -> PsiShortNamesCache.getInstance(project).getMethodsByName(JUnitUtil.SUITE_METHOD_NAME, scope));
    for (final PsiMethod method : suiteMethods) {
      ApplicationManager.getApplication().runReadAction(() -> {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return;
        if (containingClass instanceof PsiAnonymousClass) return;
        if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) return;
        if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) return;
        if (JUnitUtil.isSuiteMethod(method) && testClassFilter.isAccepted(containingClass)) {
          found.add(containingClass);
        }
      });
    }

    Set<PsiClass> processed = new HashSet<>();
    boolean hasJunit4 = addAnnotatedMethodsAnSubclasses(scope, testClassFilter, module, found, processed, JUnitUtil.TEST_ANNOTATION,
                                                        manager.getProject());
    hasJunit4 |= addAnnotatedMethodsAnSubclasses(scope, testClassFilter, module, found, processed, JUnitUtil.RUN_WITH, manager.getProject());
    return hasJunit4;
  }

  private static boolean addAnnotatedMethodsAnSubclasses(final GlobalSearchScope scope,
                                                         final TestClassFilter testClassFilter,
                                                         @Nullable final Module module,
                                                         final Set<? super PsiClass> found,
                                                         final Set<? super PsiClass> processed,
                                                         final String annotation,
                                                         final Project project) {
    final Ref<Boolean> isJUnit4 = new Ref<>(Boolean.FALSE);
    // annotated with @Test
    final PsiClass testAnnotation = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findClass(annotation, GlobalSearchScope.allScope(project)));
    if (testAnnotation != null) {
      //allScope is used to find all abstract test cases which probably have inheritors in the current 'scope'
      GlobalSearchScope allScope = module == null ? GlobalSearchScope.allScope(project)
                                                  : module.getModuleRuntimeScope(true);
      ClassesWithAnnotatedMembersSearch.search(testAnnotation, allScope).forEach(annotated -> {
        boolean success = ReadAction.compute(()-> {
          if (!processed.add(annotated)) { // don't process the same class twice regardless of it being in the scope
            return false;
          }
          final VirtualFile file = PsiUtilCore.getVirtualFile(annotated);
          if (file != null && scope.contains(file) && testClassFilter.isAccepted(annotated)) {
            if (!found.add(annotated)) {
              return false;
            }
            isJUnit4.set(Boolean.TRUE);
          }
          return true;
        });
        if (!success) return true;
        ClassInheritorsSearch.search(annotated, scope, true, true, false).forEach(new ReadActionProcessor<>() {
          @Override
          public boolean processInReadAction(PsiClass aClass) {
            if (testClassFilter.isAccepted(aClass)) {
              found.add(aClass);
              processed.add(aClass);
              isJUnit4.set(Boolean.TRUE);
            }
            return true;
          }
        });
        return true;
      });
    }

    return isJUnit4.get().booleanValue();
  }
}
