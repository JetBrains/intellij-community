// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PackageInfoWithoutPackageInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("package.info.without.package.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new PackageInfoWithoutPackageFix((String)infos[0]);
  }

  private static class PackageInfoWithoutPackageFix extends InspectionGadgetsFix {

    private final String myPackageName;

    PackageInfoWithoutPackageFix(String packageName) {
      myPackageName = packageName;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("package.info.without.package.quickfix", myPackageName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("package.info.without.package.family.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiJavaFile file)) {
        return;
      }
      file.setPackageName(myPackageName);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageInfoWithoutPackageVisitor();
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiPackage.PACKAGE_INFO_FILE.equals(file.getName());
  }

  private static class PackageInfoWithoutPackageVisitor extends BaseInspectionVisitor {

    @Override
    public void visitJavaFile(@NotNull PsiJavaFile file) {
      @NonNls final String name = file.getName();
      if (!PsiPackage.PACKAGE_INFO_FILE.equals(name)) {
        return;
      }
      final PsiPackageStatement packageStatement = file.getPackageStatement();
      if (packageStatement != null) {
        return;
      }
      final PsiDirectory directory = file.getContainingDirectory();
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == null) {
        return;
      }
      final String packageName = aPackage.getQualifiedName();
      if (packageName.isEmpty() || !PsiDirectoryFactory.getInstance(file.getProject()).isValidPackageName(packageName) || !isValid(file)) {
        return;
      }
      registerError(file, packageName);
    }

    private static boolean isValid(PsiJavaFile file) {
      if (PsiUtil.isModuleFile(file)) {
        return false;
      }
      final PsiImportList importList = file.getImportList();
      if (importList == null) { // module-info.java always has import list even if empty
        return false;
      }
      final PsiElement sibling = importList.getPrevSibling();
      if (!(sibling instanceof PsiComment)) {
        return true;
      }
      final String text = sibling.getText();
      return !text.startsWith("/*") || text.endsWith("*/");
    }
  }
}
