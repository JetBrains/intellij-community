/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ParameterizedParametersStaticCollectionInspection extends BaseInspection {

  protected static final String PARAMETERS_FQN = "org.junit.runners.Parameterized.Parameters";
  private static final String PARAMETERIZED_FQN = "org.junit.runners.Parameterized";

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(final Project project, ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null && infos[1] instanceof PsiType) {
          PsiType type = (PsiType)infos[1];
          final ChangeSignatureProcessor csp =
            new ChangeSignatureProcessor(project, method, false, PsiModifier.PUBLIC, method.getName(), type, new ParameterInfoImpl[0]);
          csp.run();
        }
        else {
          final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
          if (psiClass != null) {
            final CreateMethodQuickFix fix = CreateMethodQuickFix
              .createFix(psiClass, "@" + PARAMETERS_FQN + " public static java.util.Collection parameters()", "");
            if (fix != null) {
              fix.applyFix(project, descriptor);
            }
          }
        }
      }

      @Override
      @NotNull
      public String getName() {
        return infos.length > 0 ? (String)infos[0] : "Create @Parameterized.Parameters data provider";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return "Fix data provider signature";
      }
    };
  }

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
                List<ParameterizedParametersStaticCollectionInspection.MethodCandidate> candidates = new ArrayList<>();
                for (PsiMethod method : aClass.getMethods()) {
                  PsiType returnType = method.getReturnType();
                  final PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
                  final Project project = aClass.getProject();
                  final PsiClass collectionsClass =
                    JavaPsiFacade.getInstance(project).findClass(Collection.class.getName(), GlobalSearchScope.allScope(project));
                  if (AnnotationUtil.isAnnotated(method, PARAMETERS_FQN, 0)) {
                    final PsiModifierList modifierList = method.getModifierList();
                    String fixMessage = "Make method \'" + method.getName() + "\' ";
                    String errorString = "Method \'#ref()\' should";
                    String signatureDescription = "";
                    if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                      signatureDescription += PsiModifier.PUBLIC;
                      errorString += " be ";
                    }
                    if (!modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                      if (!signatureDescription.isEmpty()) {
                        signatureDescription += " " + PsiModifier.STATIC;
                      }
                      else {
                        signatureDescription += PsiModifier.STATIC;
                        errorString += " be ";
                      }
                    }

                    if (collectionsClass != null &&
                        !(returnType instanceof PsiArrayType) &&
                        (returnTypeClass == null || !InheritanceUtil.isInheritorOrSelf(returnTypeClass, collectionsClass, true))) {
                      if (!signatureDescription.isEmpty()) {
                        signatureDescription += " and";
                      }
                      signatureDescription += " return Collection";
                      returnType = JavaPsiFacade.getElementFactory(project).createType(collectionsClass);
                    }
                    if (!signatureDescription.isEmpty()) {
                      candidates.add(new MethodCandidate(method, fixMessage + signatureDescription, errorString + signatureDescription, returnType));
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

    MethodCandidate(PsiMethod method, String problem, String errorString, PsiType returnType) {
      myMethod = method;
      myProblem = problem;
      myErrorString = errorString;
      myReturnType = returnType;
    }
  }
}