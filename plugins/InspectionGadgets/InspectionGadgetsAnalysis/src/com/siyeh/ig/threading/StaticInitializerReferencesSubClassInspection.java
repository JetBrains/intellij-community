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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * see https://bugs.openjdk.java.net/browse/JDK-8037567
 * @author peter
 */
public class StaticInitializerReferencesSubClassInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitField(PsiField field) {
        checkSubClassReferences(field);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        checkSubClassReferences(initializer);
      }

      private void checkSubClassReferences(PsiMember scope) {
        if (!scope.hasModifierProperty(PsiModifier.STATIC)) return;

        PsiClass containingClass = scope.getContainingClass();
        Pair<PsiElement, PsiClass> pair = findSubClassReference(scope, containingClass);
        if (pair != null) {
          holder.registerProblem(pair.first,
                                 "Referencing subclass " + pair.second.getName() + " from superclass " + containingClass.getName() + " initializer might lead to class loading deadlock");
        }
      }
    };
  }

  @Nullable
  private static Pair<PsiElement, PsiClass> findSubClassReference(@NotNull PsiElement scope, @Nullable final PsiClass baseClass) {
    if (baseClass == null || baseClass.isInterface()) return null;

    final Ref<Pair<PsiElement, PsiClass>> result = Ref.create();
    scope.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof PsiClass || element instanceof PsiReferenceParameterList || element instanceof PsiTypeElement) return;

        PsiClass targetClass = extractClass(element);
        if (targetClass != null && targetClass.isInheritor(baseClass, true) && !hasSingleInitializationPlace(targetClass)) {
          PsiElement problemElement = calcProblemElement(element);
          if (problemElement != null) {
            result.set(Pair.create(problemElement, targetClass));
          }
        }

        super.visitElement(element);
      }
    });
    return result.get();
  }

  private static boolean hasSingleInitializationPlace(@NotNull PsiClass targetClass) {
    if (targetClass instanceof PsiAnonymousClass) return true;
    if (!targetClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;

    PsiFile file = targetClass.getContainingFile();
    if (file == null) return false;

    LocalSearchScope scope = new LocalSearchScope(file);
    return ReferencesSearch.search(targetClass, scope).forEach(new Processor<PsiReference>() {
      int count = 0;
      @Override
      public boolean process(PsiReference reference) {
        return ++count < 2;
      }
    });
  }

  @Nullable
  private static PsiElement calcProblemElement(PsiElement element) {
    if (element instanceof PsiNewExpression) return calcProblemElement(((PsiNewExpression)element).getClassOrAnonymousClassReference());
    if (element instanceof PsiMethodCallExpression) return calcProblemElement(((PsiMethodCallExpression)element).getMethodExpression());
    if (element instanceof PsiJavaCodeReferenceElement) return ((PsiJavaCodeReferenceElement)element).getReferenceNameElement();
    return element;
  }

  @Nullable
  private static PsiClass extractClass(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)element).resolve();
      if (target instanceof PsiClass) {
        return (PsiClass)target;
      }
    }
    if (element instanceof PsiExpression) {
      return PsiUtil.resolveClassInClassTypeOnly(((PsiExpression)element).getType());
    }
    return null;
  }
}
