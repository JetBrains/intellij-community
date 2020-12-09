// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.inspections;

import com.intellij.appengine.JavaGoogleAppEngineBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AppEngineForbiddenCodeInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final Project project = manager.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    final AppEngineFacet appEngineFacet = AppEngineFacet.getAppEngineFacetByModule(module);
    if (appEngineFacet == null) {
      return null;
    }
    final AppEngineSdk appEngineSdk = appEngineFacet.getSdk();
    if (!appEngineSdk.isValid()) {
      return null;
    }

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final List<ProblemDescriptor> problems = new ArrayList<>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitDocComment(PsiDocComment comment) {
      }

      @Override
      public void visitMethod(PsiMethod method) {
        final PsiModifierList modifierList = method.getModifierList();
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
          if (!isNativeMethodAllowed(method)) {
            problems.add(manager.createProblemDescriptor(modifierList, JavaGoogleAppEngineBundle.message(
              "inspection.message.native.methods.not.allowed"), isOnTheFly,
                                                         LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
        super.visitMethod(method);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) {
          final PsiElement resolved = classReference.resolve();
          if (resolved instanceof PsiClass) {
            final String qualifiedName = ((PsiClass)resolved).getQualifiedName();
            if (qualifiedName != null && appEngineSdk.isMethodInBlacklist(qualifiedName, "new")) {
              final String message = JavaGoogleAppEngineBundle
                .message("inspection.message.application.should.not.create.new.instances.0", qualifiedName);
              problems.add(manager.createProblemDescriptor(classReference, message, isOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
        super.visitNewExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement element = methodExpression.resolve();
        if (element instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element;
          final PsiClass psiClass = method.getContainingClass();
          if (psiClass != null) {
            final String qualifiedName = psiClass.getQualifiedName();
            final String methodName = method.getName();
            if (qualifiedName != null && appEngineSdk.isMethodInBlacklist(qualifiedName, methodName)) {
              final String message = JavaGoogleAppEngineBundle.message("inspection.message.application.should.not.call.method",
                                                                       StringUtil.getShortName(qualifiedName), methodName);
              problems.add(manager.createProblemDescriptor(methodExpression, message, isOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
          final PsiFile psiFile = resolved.getContainingFile();
          if (psiFile != null) {
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null && !fileIndex.isInSource(virtualFile)) {
              final List<OrderEntry> list = fileIndex.getOrderEntriesForFile(virtualFile);
              for (OrderEntry entry : list) {
                if (entry instanceof JdkOrderEntry) {
                  final String className = ClassUtil.getJVMClassName((PsiClass)resolved);
                  if (className != null && !appEngineSdk.isClassInWhiteList(className)) {
                    problems.add(manager.createProblemDescriptor(reference,
                                                                 JavaGoogleAppEngineBundle
                                                                   .message("inspection.message.class.not.included.in.white.list", className),
                                                                 isOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                  }
                }
              }
            }
          }
        }
        super.visitReferenceElement(reference);
      }
    });
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static boolean isNativeMethodAllowed(PsiMethod method) {
    for (AppEngineForbiddenCodeHandler handler : AppEngineForbiddenCodeHandler.EP_NAME.getExtensions()) {
      if (handler.isNativeMethodAllowed(method)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return JavaGoogleAppEngineBundle.message("plugin.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "AppEngineForbiddenCode";
  }
}
