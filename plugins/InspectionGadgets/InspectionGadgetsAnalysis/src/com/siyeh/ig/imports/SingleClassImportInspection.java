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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
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
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageImportVisitor();
  }

  private static class PackageImportVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile file = (PsiJavaFile)parent;
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