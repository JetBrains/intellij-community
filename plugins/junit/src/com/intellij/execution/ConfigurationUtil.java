/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Processor;
import junit.runner.BaseTestRunner;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ConfigurationUtil {
  // return true if there is JUnit4 test
  public static boolean findAllTestClasses(final TestClassFilter testClassFilter, final Set<PsiClass> found) {
    final PsiManager manager = testClassFilter.getPsiManager();

    GlobalSearchScope projectScopeWithoutLibraries = GlobalSearchScope.projectScope(manager.getProject());
    final GlobalSearchScope scope = projectScopeWithoutLibraries.intersectWith(testClassFilter.getScope());
    ClassInheritorsSearch.search(testClassFilter.getBase(), scope, true).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
      public boolean execute(final PsiClass aClass) {
        if (testClassFilter.isAccepted(aClass)) found.add(aClass);
        return true;
      }
    }));

    // classes having suite() method
    final PsiMethod[] suiteMethods = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiMethod[]>() {
          public PsiMethod[] compute() {
            return JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache().getMethodsByName(BaseTestRunner.SUITE_METHODNAME, scope);
          }
        }
    );
    for (final PsiMethod method : suiteMethods) {
      final PsiClass containingClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        public PsiClass compute() {
          return method.getContainingClass();
        }
      });
      if (containingClass == null) continue;
      if (containingClass instanceof PsiAnonymousClass) continue;
      if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return JUnitUtil.isSuiteMethod(method);
        }
      }).booleanValue()) {
        found.add(containingClass);
      }
    }

    boolean hasJunit4 = addAnnotatedMethodsAnSubclasses(manager, scope, testClassFilter, found, "org.junit.Test", true);
    hasJunit4 |= addAnnotatedMethodsAnSubclasses(manager, scope, testClassFilter, found, "org.junit.runner.RunWith", false);
    return hasJunit4;
  }

  private static boolean addAnnotatedMethodsAnSubclasses(final PsiManager manager, final GlobalSearchScope scope, final TestClassFilter testClassFilter,
                                                         final Set<PsiClass> found,
                                                         final String annotation,
                                                         final boolean isMethod) {
    final Ref<Boolean> isJUnit4 = new Ref<Boolean>(Boolean.FALSE);
    // annotated with @Test
    final PsiClass testAnnotation = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiClass>() {
          @Nullable
          public PsiClass compute() {
            return JavaPsiFacade.getInstance(manager.getProject()).findClass(annotation, GlobalSearchScope.allScope(manager.getProject()));
          }
        }
    );
    if (testAnnotation != null) {
     ApplicationManager.getApplication().runReadAction(new Runnable() {
         public void run(){
              AnnotatedMembersSearch.search(testAnnotation, GlobalSearchScope.allScope(manager.getProject())).forEach(new Processor<PsiMember>() {
             public boolean process(final PsiMember annotated) {
               PsiClass containingClass = annotated instanceof PsiClass ? (PsiClass)annotated : annotated.getContainingClass();
               if (containingClass != null && annotated instanceof PsiMethod == isMethod) {
                 if (scope.contains(PsiUtilBase.getVirtualFile(containingClass)) && testClassFilter.isAccepted(containingClass)) {
                   found.add(containingClass);
                   isJUnit4.set(Boolean.TRUE);
                 }
                 ClassInheritorsSearch.search(containingClass, scope, true).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
                   public boolean execute(final PsiClass aClass) {
                     if (testClassFilter.isAccepted(aClass)) {
                       found.add(aClass);
                       isJUnit4.set(Boolean.TRUE);
                     }
                     return true;
                   }
                 }));
               }
               return true;
             }
           });
         }
     });

    }

    return isJUnit4.get().booleanValue();
  }
}
