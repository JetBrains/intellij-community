// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreatePackageInfoAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PackageGlobalInspection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class MissingPackageInfoInspection extends PackageGlobalInspection {

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalMissingPackageInfoInspection(this);
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    final String packageName = refPackage.getQualifiedName();
    final Project project = globalInspectionContext.getProject();
    final PsiPackage aPackage = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findPackage(packageName));
    boolean needsPackageInfo = ReadAction.compute(() -> !hasPackageInfoFile(aPackage) && aPackage.getClasses().length > 0);
    if (!needsPackageInfo) {
      return null;
    }
    if (PsiUtil.isLanguageLevel5OrHigher(aPackage)) {
      return new CommonProblemDescriptor[] {
        inspectionManager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageName))};
    }
    else {
      return new CommonProblemDescriptor[] {
        inspectionManager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageName))};
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

  private static class LocalMissingPackageInfoInspection extends BaseSharedLocalInspection<MissingPackageInfoInspection> {

    LocalMissingPackageInfoInspection(MissingPackageInfoInspection settingsDelegate) {
      super(settingsDelegate);
    }

    @Nullable
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
      return new InspectionGadgetsFix() {
        @NotNull
        @Override
        public String getFamilyName() {
          return InspectionGadgetsBundle.message("create.package.info.java.family.name");
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          DataManager.getInstance()
                     .getDataContextFromFocusAsync()
                     .onSuccess(context -> {
                       final AnActionEvent event = new AnActionEvent(null, context, "", new Presentation(), ActionManager.getInstance(), 0);
                       new CreatePackageInfoAction().actionPerformed(event);
                     });
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
          Icon icon = FileTypeRegistry.getInstance().getFileTypeByFileName("package-info.java").getIcon();
          HtmlChunk fragment = HtmlChunk.fragment(HtmlChunk.text(getFamilyName()), HtmlChunk.icon("file", icon));
          return new IntentionPreviewInfo.Html(fragment);
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
        public void visitJavaFile(@NotNull PsiJavaFile file) {
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
