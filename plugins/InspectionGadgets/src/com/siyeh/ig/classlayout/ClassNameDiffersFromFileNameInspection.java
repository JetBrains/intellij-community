/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassNameDiffersFromFileNameInspection extends BaseInspection {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiJavaFile file = (PsiJavaFile)infos[0];
    final String fileName = file.getName();
    final int prefixIndex = fileName.indexOf('.');
    final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
    final PsiClass[] classes = file.getClasses();
    for (PsiClass psiClass : classes) {
      final String className = psiClass.getName();
      if (filenameWithoutPrefix.equals(className)) {
        return null;
      }
    }
    return new RenameFix(filenameWithoutPrefix);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.name.differs.from.file.name.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNameDiffersFromFileNameVisitor();
  }

  private static class ClassNameDiffersFromFileNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiJavaFile file)) {
        return;
      }
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      final String fileName = file.getName();
      final int prefixIndex = fileName.indexOf('.');
      if (prefixIndex < 0) {
        return;
      }
      final String filenameWithoutPrefix =
        fileName.substring(0, prefixIndex);
      if (className.equals(filenameWithoutPrefix)) {
        return;
      }
      registerClassError(aClass, file);
    }
  }
}