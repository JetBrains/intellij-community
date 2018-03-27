/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public class InheritanceUtil {

  private InheritanceUtil() {}

  public static boolean existsMutualSubclass(PsiClass class1, final PsiClass class2, final boolean avoidExpensiveProcessing) {
    if (class1 instanceof PsiTypeParameter) {
      final PsiClass[] superClasses = class1.getSupers();
      for (PsiClass superClass : superClasses) {
        if (!existsMutualSubclass(superClass, class2, avoidExpensiveProcessing)) {
          return false;
        }
      }
      return true;
    }
    if (class2 instanceof PsiTypeParameter) {
      return existsMutualSubclass(class2, class1, avoidExpensiveProcessing);
    }

    final String className = class1.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(className)) {
      return true;
    }
    final String class2Name = class2.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(class2Name)) {
      return true;
    }
    if (class1.isInheritor(class2, true) || class2.isInheritor(class1, true)) {
      return true;
    }
    final SearchScope scope = GlobalSearchScope.allScope(class1.getProject());
    final Query<PsiClass> search = ClassInheritorsSearch.search(class1, scope, true);
    final boolean[] result = new boolean[1];
    search.forEach(new Processor<PsiClass>() {
      AtomicInteger count = new AtomicInteger(0);

      @Override
      public boolean process(PsiClass inheritor) {
        if (inheritor.equals(class2) || inheritor.isInheritor(class2, true) || avoidExpensiveProcessing && count.incrementAndGet() > 20) {
          result[0] = true;
          return false;
        }
        return true;
      }
    });
    return result[0];
  }

  public static boolean hasImplementation(@NotNull PsiClass aClass) {
    final SearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
    if (aClass.isInterface() && FunctionalExpressionSearch.search(aClass, scope).findFirst() != null) return true;
    for (ImplicitSubclassProvider provider : ImplicitSubclassProvider.EP_NAME.getExtensions()) {
      if (!provider.isApplicableTo(aClass)) {
        continue;
      }
      ImplicitSubclassProvider.SubclassingInfo info = provider.getSubclassingInfo(aClass);
      if (info != null && !info.isAbstract()) {
        return true;
      }
    }
    final Query<PsiClass> search = ClassInheritorsSearch.search(aClass, scope, true);
    return !search.forEach(
      inheritor -> inheritor.isInterface() || inheritor.isAnnotationType() || inheritor.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  public static boolean hasOneInheritor(final PsiClass aClass) {
    final CountingProcessor processor = new CountingProcessor(2);
    ProgressManager.getInstance().runProcess(
      (Runnable)() -> ClassInheritorsSearch.search(aClass, aClass.getUseScope(), false).forEach(processor), null);
    return processor.getCount() == 1;
  }

  private static class CountingProcessor implements Processor<PsiClass> {
    private final AtomicInteger myCount = new AtomicInteger(0);
    private final int myLimit;

    CountingProcessor(int limit) {
      myLimit = limit;
    }

    public int getCount() {
      return myCount.get();
    }

    @Override
    public boolean process(PsiClass aClass) {
      return myCount.incrementAndGet() < myLimit;
    }
  }
}