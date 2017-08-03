/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PackageInfoWithoutPackageInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("package.info.java.without.package.display.name");
  }

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

    public PackageInfoWithoutPackageFix(String packageName) {
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
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile file = (PsiJavaFile)element;
      // can't use file.setPackageName(myPackageName) here because it will put the package in the wrong place and screw up formatting.
      PsiElement anchor = file.getFirstChild();
      while (anchor instanceof PsiWhiteSpace || anchor instanceof PsiComment) {
        anchor = anchor.getNextSibling();
      }
      final PsiPackageStatement packageStatement = JavaPsiFacade.getElementFactory(project).createPackageStatement(myPackageName);
      file.addBefore(packageStatement, anchor);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageInfoWithoutPackageVisitor();
  }

  private static class PackageInfoWithoutPackageVisitor extends BaseInspectionVisitor {

    @Override
    public void visitJavaFile(PsiJavaFile file) {
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
      if (packageName.isEmpty()) {
        return;
      }
      registerError(file, packageName);
    }
  }
}
