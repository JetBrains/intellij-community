// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ParameterizedParametersStaticCollectionInspection extends BaseInspection {

  protected static final String PARAMETERS_FQN = "org.junit.runners.Parameterized.Parameters";
  private static final String PARAMETERIZED_FQN = "org.junit.runners.Parameterized";

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    if (infos[0] instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)infos[0];
      return new DelegatingFix(CreateMethodQuickFix.createFix(aClass, "@" + PARAMETERS_FQN + " public static java.lang.Iterable<java.lang.Object[]> parameters()", ""));
    }
    return new InspectionGadgetsFix() {

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @Override
      protected void doFix(final Project project, ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement().getParent();
        if (!(element instanceof PsiMethod)) {
          return;
        }
        final PsiMethod method = (PsiMethod)element;
        final PsiType type = (PsiType)infos[2];
        final ChangeSignatureProcessor csp =
          new ChangeSignatureProcessor(project, method, false, PsiModifier.PUBLIC, method.getName(), type, new ParameterInfoImpl[0]);
        csp.run();
      }

      @Override
      @NotNull
      public String getName() {
        return (String)infos[0];
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("fix.data.provider.signature.family.name");
      }
    };
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return infos.length > 1
           ? (String)infos[1]
           : InspectionGadgetsBundle.message("fix.data.provider.signature.problem");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, "org.junit.runner.RunWith");
        if (annotation == null) {
          return;
        }
        final PsiNameValuePair pair = AnnotationUtil.findDeclaredAttribute(annotation, null);
        if (pair == null) {
          return;
        }
        final PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        final PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)value).getOperand();
        if (!typeElement.getType().getCanonicalText().equals(PARAMETERIZED_FQN)) {
          return;
        }
        final List<MethodCandidate> candidates = new ArrayList<>();
        final Project project = aClass.getProject();
        final PsiClass iterableClass =
          JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_ITERABLE, GlobalSearchScope.allScope(project));
        if (iterableClass == null) {
          return;
        }
        for (PsiMethod method : aClass.getMethods()) {
          if (!AnnotationUtil.isAnnotated(method, PARAMETERS_FQN, 0)) {
            continue;
          }
          final PsiModifierList modifierList = method.getModifierList();
          final String fixMessage = "Make method '" + method.getName() + "' ";
          String errorString = "Method '#ref()' should";
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

          PsiType returnType = method.getReturnType();
          final PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
          boolean objectArray = returnType instanceof PsiArrayType &&
                                returnType.getDeepComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
          boolean iterable = returnTypeClass != null && InheritanceUtil.isInheritorOrSelf(returnTypeClass, iterableClass, true);
          if (!objectArray && !iterable) {
            if (!signatureDescription.isEmpty()) {
              signatureDescription += " and";
            }
            signatureDescription += " have return type Iterable or Object[]";
            returnType = JavaPsiFacade.getElementFactory(project).createType(iterableClass);
          }
          if (!signatureDescription.isEmpty()) {
            candidates.add(new MethodCandidate(method, fixMessage + signatureDescription, errorString + signatureDescription, returnType));
            continue;
          }
          return;
        }
        if (candidates.isEmpty()) {
          registerClassError(aClass, aClass);
        }
        else {
          for (MethodCandidate candidate : candidates) {
            registerMethodError(candidate.myMethod, candidate.myProblem, candidate.myErrorString, candidate.myReturnType);
          }
        }
      }
    };
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