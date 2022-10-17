// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * see https://bugs.openjdk.org/browse/JDK-8037567
 */
public class StaticInitializerReferencesSubClassInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitField(@NotNull PsiField field) {
        checkSubClassReferences(field);
      }

      @Override
      public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
        checkSubClassReferences(initializer);
      }

      private void checkSubClassReferences(PsiMember scope) {
        if (!scope.hasModifierProperty(PsiModifier.STATIC)) return;

        PsiClass containingClass = scope.getContainingClass();
        Pair<PsiElement, PsiClass> pair = findSubClassReference(scope, containingClass);
        if (pair != null) {
          holder.registerProblem(pair.first,
                                 InspectionGadgetsBundle
                                   .message("referencing.subclass.0.from.superclass.1.initializer.might.lead.to.class.loading.deadlock",
                                            pair.second.getName(), containingClass.getName()));
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
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiMethod ||
            element instanceof PsiReferenceParameterList ||
            element instanceof PsiTypeElement ||
            element instanceof PsiLambdaExpression) {
          return;
        }

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
    if (!targetClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;

    PsiFile file = targetClass.getContainingFile();
    if (file == null) return false;

    LocalSearchScope scope = new LocalSearchScope(file);
    return ReferencesSearch.search(targetClass, scope).forEach(new Processor<>() {
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
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(((PsiExpression)element).getType());
      return psiClass instanceof PsiAnonymousClass ? psiClass.getSuperClass() : psiClass;
    }
    return null;
  }
}
