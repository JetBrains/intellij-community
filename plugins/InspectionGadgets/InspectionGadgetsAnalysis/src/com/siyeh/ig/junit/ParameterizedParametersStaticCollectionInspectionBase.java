// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ParameterizedParametersStaticCollectionInspectionBase extends BaseInspection {
  protected static final String PARAMETERS_FQN = "org.junit.runners.Parameterized.Parameters";
  private static final String PARAMETERIZED_FQN = "org.junit.runners.Parameterized";

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return infos.length > 0
           ? (String)infos[1]
           : "Class #ref annotated @RunWith(Parameterized.class) lacks data provider";
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, "org.junit.runner.RunWith");
        if (annotation != null) {
          for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            final PsiAnnotationMemberValue value = pair.getValue();
            if (value instanceof PsiClassObjectAccessExpression) {
              final PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)value).getOperand();
              if (typeElement.getType().getCanonicalText().equals(PARAMETERIZED_FQN)) {
                List<MethodCandidate> candidates = new ArrayList<>();
                for (PsiMethod method : aClass.getMethods()) {
                  PsiType returnType = method.getReturnType();
                  final PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
                  final Project project = aClass.getProject();
                  final PsiClass collectionsClass =
                    JavaPsiFacade.getInstance(project).findClass(Collection.class.getName(), GlobalSearchScope.allScope(project));
                  if (AnnotationUtil.isAnnotated(method, PARAMETERS_FQN, 0)) {
                    final PsiModifierList modifierList = method.getModifierList();
                    boolean hasToFixSignature = false;
                    String message = "Make method \'" + method.getName() + "\' ";
                    String errorString = "Method \'#ref()\' should be ";
                    if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                      message += PsiModifier.PUBLIC + " ";
                      errorString += PsiModifier.PUBLIC + " ";
                      hasToFixSignature = true;
                    }
                    if (!modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                      message += PsiModifier.STATIC;
                      errorString += PsiModifier.STATIC;
                      hasToFixSignature = true;
                    }
                    if (collectionsClass != null &&
                        (returnTypeClass == null || !InheritanceUtil.isInheritorOrSelf(returnTypeClass, collectionsClass, true))) {
                      message += (hasToFixSignature ? " and" : "") + " return Collection";
                      errorString += (hasToFixSignature ? " and" : "") + " return Collection";
                      returnType = JavaPsiFacade.getElementFactory(project).createType(collectionsClass);
                      hasToFixSignature = true;
                    }
                    if (hasToFixSignature) {
                      candidates.add(new MethodCandidate(method, message, errorString, returnType));
                      continue;
                    }
                    return;
                  }
                }
                if (candidates.isEmpty()) {
                  registerClassError(aClass);
                }
                else {
                  for (MethodCandidate candidate : candidates) {
                    registerMethodError(candidate.myMethod, candidate.myProblem, candidate.myErrorString, candidate.myReturnType);
                  }
                }
              }
            }
          }
        }
      }
    };
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "@RunWith(Parameterized.class) without data provider";
  }

  private static class MethodCandidate {
    PsiMethod myMethod;
    String myProblem;
    private final String myErrorString;
    PsiType myReturnType;

    public MethodCandidate(PsiMethod method, String problem, String errorString, PsiType returnType) {
      myMethod = method;
      myProblem = problem;
      myErrorString = errorString;
      myReturnType = returnType;
    }
  }
}
