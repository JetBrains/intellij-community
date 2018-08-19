// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreatePackageInfoAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class MissingPackageInfoInspection extends BaseGlobalInspection {

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalMissingPackageInfoInspection(this);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("missing.package.info.display.name");
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final RefPackage refPackage = (RefPackage)refEntity;
    final String packageName = refPackage.getQualifiedName();
    final Project project = globalContext.getProject();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (hasPackageInfoFile(aPackage)) {
      return null;
    }
    final List<RefEntity> children = refPackage.getChildren();
    boolean hasClasses = false;
    for (RefEntity child : children) {
      if (child instanceof RefClass) {
        hasClasses = true;
        break;
      }
    }
    if (!hasClasses) {
      return null;
    }
    if (PsiUtil.isLanguageLevel5OrHigher(aPackage)) {
      return new CommonProblemDescriptor[] {
        manager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageName))};
    }
    else {
      return new CommonProblemDescriptor[] {
        manager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageName))};
    }
  }

  @Contract("null -> true")
  static boolean hasPackageInfoFile(PsiPackage aPackage) {
    if (aPackage == null) {
      return true;
    }
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      final boolean packageInfoFound = directory.findFile(PsiPackage.PACKAGE_INFO_FILE) != null;
      final boolean packageDotHtmlFound = directory.findFile("package.html") != null;
      if (packageInfoFound || packageDotHtmlFound) {
        return true;
      }
    }
    return false;
  }

  private static class LocalMissingPackageInfoInspection extends
                                                         BaseSharedLocalInspection<MissingPackageInfoInspection> {

    public LocalMissingPackageInfoInspection(MissingPackageInfoInspection settingsDelegate) {
      super(settingsDelegate);
    }

    @Nullable
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
      return new InspectionGadgetsFix() {
        @NotNull
        @Override
        public String getFamilyName() {
          return "Create 'package-info.java'";
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
          DataManager.getInstance()
                     .getDataContextFromFocusAsync()
                     .onSuccess(context -> {
                       final AnActionEvent event = new AnActionEvent(null, context, "", new Presentation(), ActionManager.getInstance(), 0);
                       new CreatePackageInfoAction().actionPerformed(event);
                     });
        }
      };
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
      final PsiPackageStatement packageStatement = (PsiPackageStatement)infos[0];
      if (PsiUtil.isLanguageLevel5OrHigher(packageStatement)) {
        return InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageStatement.getPackageName());
      }
      else {
        return InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageStatement.getPackageName());
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new BaseInspectionVisitor() {
        @Override
        public void visitJavaFile(PsiJavaFile file) {
          final PsiPackageStatement packageStatement = file.getPackageStatement();
          if (packageStatement == null) {
            return;
          }
          final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
          final PsiElement target = packageReference.resolve();
          if (!(target instanceof PsiPackage)) {
            return;
          }
          final PsiPackage aPackage = (PsiPackage)target;
          if (hasPackageInfoFile(aPackage)) {
            return;
          }
          registerError(packageReference, packageStatement);
        }
      };
    }
  }
}
