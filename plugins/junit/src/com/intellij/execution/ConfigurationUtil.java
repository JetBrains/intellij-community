package com.intellij.execution;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Processor;
import junit.runner.BaseTestRunner;

import java.util.Set;

public class ConfigurationUtil {
  // return true if there is JUnit4 test
  public static boolean findAllTestClasses(final TestClassFilter testClassFilter, final Set<PsiClass> found) {
    final PsiManager manager = testClassFilter.getPsiManager();
    final PsiSearchHelper searchHelper = manager.getSearchHelper();

    GlobalSearchScope projectScopeWithoutLibraries = GlobalSearchScope.projectScope(manager.getProject());
    final GlobalSearchScope scope = projectScopeWithoutLibraries.intersectWith(testClassFilter.getScope());
    searchHelper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(final PsiClass aClass) {
        if (testClassFilter.isAccepted(aClass)) found.add(aClass);
        return true;
      }
    }, testClassFilter.getBase(), scope, true);

    // classes having suite() method
    final PsiMethod[] suiteMethods = manager.getShortNamesCache().getMethodsByName(BaseTestRunner.SUITE_METHODNAME, scope);
    for (final PsiMethod method : suiteMethods) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) continue;
      if (containingClass instanceof PsiAnonymousClass) continue;
      if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (JUnitUtil.isSuiteMethod(method)) {
        found.add(containingClass);
      }
    }

    boolean hasJunit4 = addAnnotatedMethods(manager, scope, testClassFilter, found, "org.junit.Test", true);
    hasJunit4 |= addAnnotatedMethods(manager, scope, testClassFilter, found, "org.junit.runner.RunWith", false);
    return hasJunit4;
  }

  private static boolean addAnnotatedMethods(final PsiManager manager, final GlobalSearchScope scope, final TestClassFilter testClassFilter,
                                             final Set<PsiClass> found, final String annotation, final boolean isMethod) {
    final Ref<Boolean> isJUnit4 = new Ref<Boolean>(Boolean.FALSE);
    // annotated with @Test
    PsiClass testAnnotation = manager.findClass(annotation, GlobalSearchScope.allScope(manager.getProject()));
    if (testAnnotation != null) {
      AnnotatedMembersSearch.search(testAnnotation, scope).forEach(new Processor<PsiMember>() {
        public boolean process(final PsiMember annotated) {
          PsiClass containingClass = annotated instanceof PsiClass ? (PsiClass)annotated : annotated.getContainingClass();
          if (containingClass != null
              && annotated instanceof PsiMethod == isMethod
              && testClassFilter.isAccepted(containingClass)) {
            found.add(containingClass);
            isJUnit4.set(Boolean.TRUE);
          }
          return true;
        }
      });
    }

    return isJUnit4.get().booleanValue();
  }
}
