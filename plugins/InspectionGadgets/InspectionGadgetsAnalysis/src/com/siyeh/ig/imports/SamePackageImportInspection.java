/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.imports;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class SamePackageImportInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "import.from.same.package.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "import.from.same.package.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteImportFix();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SamePackageImportVisitor();
  }

  private static class SamePackageImportVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitImportList(PsiImportList importList) {
      final PsiElement parent = importList.getParent();
      if (!(parent instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile javaFile = (PsiJavaFile)parent;
      final String packageName = javaFile.getPackageName();
      final PsiImportStatement[] importStatements =
        importList.getImportStatements();
      for (final PsiImportStatement importStatement : importStatements) {
        final PsiJavaCodeReferenceElement reference =
          importStatement.getImportReference();
        if (reference == null) {
          continue;
        }
        final String text = importStatement.getQualifiedName();
        if (importStatement.isOnDemand()) {
          if (packageName.equals(text)) {
            registerError(importStatement);
          }
        }
        else {
          if (text == null) {
            return;
          }
          final int classNameIndex = text.lastIndexOf((int)'.');
          final String parentName;
          if (classNameIndex < 0) {
            parentName = "";
          }
          else {
            parentName = text.substring(0, classNameIndex);
          }
          if (packageName.equals(parentName)) {
            registerError(importStatement);
          }
        }
      }
    }
  }
}
