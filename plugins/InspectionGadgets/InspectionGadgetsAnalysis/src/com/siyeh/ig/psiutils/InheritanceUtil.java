/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class InheritanceUtil {

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

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(class1.getQualifiedName())) {
      return true;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(class2.getQualifiedName())) {
      return true;
    }
    if (class1.isInheritor(class2, true) || class2.isInheritor(class1, true) || Objects.equals(class1, class2)) {
      return true;
    }
    final SearchScope scope = GlobalSearchScope.allScope(class1.getProject());
    String class1Name = class1.getName();
    String class2Name = class2.getName();
    if (class1Name == null || class2Name == null) {
      // One of classes is anonymous? No subclass is possible
      return false;
    }
    if (class1.hasModifierProperty(PsiModifier.FINAL) || class2.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (LambdaUtil.isFunctionalClass(class1) || class1Name.length() < class2Name.length() ||
        (isJavaClass(class2) && !isJavaClass(class1))) {
      // Assume that it could be faster to search inheritors from non-functional interface or from class with a longer simple name
      // Also prefer searching inheritors from Java class over other JVM languages as Java is usually faster
      return doSearch(class2, class1, avoidExpensiveProcessing, scope);
    }
    return doSearch(class1, class2, avoidExpensiveProcessing, scope);
  }

  private static boolean isJavaClass(PsiClass class1) {
    return class1 instanceof PsiClassImpl || class1 instanceof ClsClassImpl;
  }

  public static boolean doSearch(PsiClass class1, PsiClass class2, boolean avoidExpensiveProcessing, SearchScope scope) {
    final Query<PsiClass> search = ClassInheritorsSearch.search(class1, scope, true);
    final boolean[] result = new boolean[1];
    search.forEach(new Processor<PsiClass>() {
      final AtomicInteger count = new AtomicInteger(0);

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
    for (ImplicitSubclassProvider provider : ImplicitSubclassProvider.EP_NAME.getExtensions()) {
      if (!provider.isApplicableTo(aClass)) {
        continue;
      }
      ImplicitSubclassProvider.SubclassingInfo info = provider.getSubclassingInfo(aClass);
      if (info != null && !info.isAbstract()) {
        return true;
      }
    }
    return ClassInheritorsSearch.search(aClass).anyMatch(inheritor -> !inheritor.isInterface() &&
                                                                      !inheritor.isAnnotationType() &&
                                                                      !inheritor.hasModifierProperty(PsiModifier.ABSTRACT))
           || aClass.isInterface() && FunctionalExpressionSearch.search(aClass).findFirst() != null;
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