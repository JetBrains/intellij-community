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

import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnusedImportInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unused.import.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unused.import.problem.descriptor");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
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
    return new UnusedImportVisitor();
  }

  private static class UnusedImportVisitor extends BaseInspectionVisitor {

    @Override
    public void visitJavaFile(PsiJavaFile file) {
      final PsiClass[] classes = file.getClasses();
      final PsiPackageStatement packageStatement = file.getPackageStatement();
      final PsiModifierList annotationList;
      if (packageStatement != null) {
        annotationList = packageStatement.getAnnotationList();
      }
      else {
        annotationList = null;
      }
      checkImports(file, classes, annotationList);
    }

    private void checkImports(PsiJavaFile file, PsiClass[] classes, @Nullable PsiModifierList annotationList) {
      final ImportsAreUsedVisitor visitor = new ImportsAreUsedVisitor(file);
      for (PsiClass aClass : classes) {
        aClass.accept(visitor);
      }
      if (annotationList != null) {
        annotationList.accept(visitor);
      }
      for (PsiImportStatementBase unusedImportStatement : visitor.getUnusedImportStatements()) {
        if (unusedImportStatement.getImportReference() != null && 
            !(PsiTreeUtil.skipWhitespacesForward(unusedImportStatement) instanceof PsiErrorElement)) {
          registerError(unusedImportStatement);
        }
      }
    }
  }
}