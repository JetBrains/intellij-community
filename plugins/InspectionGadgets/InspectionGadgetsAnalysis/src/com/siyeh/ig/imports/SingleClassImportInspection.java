/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NotNull;

public class SingleClassImportInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "single.class.import.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "single.class.import.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageImportVisitor();
  }

  private static class PackageImportVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (!(aClass.getParent() instanceof PsiJavaFile)) {
        return;
      }
      if (FileTypeUtils.isInServerPageFile(aClass.getContainingFile())) {
        return;
      }
      final PsiJavaFile file = (PsiJavaFile)aClass.getParent();
      if (file == null) {
        return;
      }
      if (!file.getClasses()[0].equals(aClass)) {
        return;
      }
      final PsiImportList importList = file.getImportList();
      if (importList == null) {
        return;
      }
      final PsiImportStatement[] importStatements =
        importList.getImportStatements();
      for (final PsiImportStatement importStatement : importStatements) {
        if (!importStatement.isOnDemand()) {
          registerError(importStatement);
        }
      }
    }
  }
}