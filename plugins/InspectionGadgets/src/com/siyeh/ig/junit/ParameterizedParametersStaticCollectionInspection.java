// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ParameterizedParametersStaticCollectionInspection extends BaseInspection {

  protected static final String PARAMETERS_FQN = "org.junit.runners.Parameterized.Parameters";

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    if (infos.length == 0) return null;
    if (infos[0] instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)infos[0];
      final String signature = "@" + PARAMETERS_FQN + " public static java.lang.Iterable<java.lang.Object[]> parameters()";
      return new DelegatingFix(CreateMethodQuickFix.createFix(aClass, signature, "")) {
        @Override
        public @NotNull String getName() {
          return getFamilyName();
        }

        @Override
        public @NotNull String getFamilyName() {
          return InspectionGadgetsBundle.message("fix.data.provider.create.method.fix.name");
        }
      };
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
        WriteAction.run(() -> {
          final VirtualFile vFile = method.getContainingFile().getVirtualFile();
          if (ReadonlyStatusHandler.getInstance(method.getProject()).ensureFilesWritable(List.of(vFile)).hasReadonlyFiles()) {
            return;
          }
          method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        });
        final PsiType type = (PsiType)infos[1];
        var provider = JavaSpecialRefactoringProvider.getInstance();
        var csp = provider.getChangeSignatureProcessorWithCallback(project,
                                                                        method,
                                                                        false,
                                                                        PsiModifier.PUBLIC,
                                                                        method.getName(),
                                                                        type,
                                                                        new ParameterInfoImpl[0],
                                                                        true,
                                                                        null);
        csp.run();
      }

      @Override
      @NotNull
      public String getName() {
        return InspectionGadgetsBundle.message("fix.data.provider.signature.fix.name", infos[0]);
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
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("fix.data.provider.multiple.methods.problem");
    }
    return infos.length > 1
           ? InspectionGadgetsBundle.message("fix.data.provider.signature.incorrect.problem")
           : InspectionGadgetsBundle.message("fix.data.provider.signature.missing.method.problem");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (!TestUtils.isParameterizedTest(aClass)) {
          return;
        }
        final Project project = aClass.getProject();
        final PsiClass iterableClass =
          JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_ITERABLE, GlobalSearchScope.allScope(project));
        if (iterableClass == null) {
          return;
        }
        int methodFound = 0;
        for (PsiMethod method : aClass.getMethods()) {
          if (!AnnotationUtil.isAnnotated(method, PARAMETERS_FQN, 0)) {
            continue;
          }
          methodFound++;
          final boolean notPublic = !method.hasModifierProperty(PsiModifier.PUBLIC);
          final boolean notStatic = !method.hasModifierProperty(PsiModifier.STATIC);

          PsiType returnType = method.getReturnType();
          final PsiClass returnTypeClass = PsiUtil.resolveClassInType(returnType);
          final boolean objectArray = returnType instanceof PsiArrayType &&
                                      returnType.getDeepComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
          final boolean iterable = returnTypeClass != null && InheritanceUtil.isInheritorOrSelf(returnTypeClass, iterableClass, true);
          final String signatureText;
          if (!objectArray && !iterable) {
            signatureText = "public static Iterable<Object[]> " + method.getName() + "()";
            returnType = JavaPsiFacade.getElementFactory(project)
              .createTypeFromText(CommonClassNames.JAVA_LANG_ITERABLE + "<java.lang.Object[]>", method);
          }
          else {
            signatureText = "public static " + returnType.getPresentableText() + " " + method.getName() + "()";
          }
          if (notPublic || notStatic || (!objectArray && !iterable)) {
            registerMethodError(method, signatureText, returnType);
          }
        }
        if (methodFound == 0) {
          registerClassError(aClass, aClass);
        }
        else if (methodFound > 1) {
          registerClassError(aClass);
        }
      }
    };
  }
}