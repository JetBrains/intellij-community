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
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.Nullable;

public class ClassNameDiffersFromFileNameInspection extends ClassNameDiffersFromFileNameInspectionBase {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiJavaFile file = (PsiJavaFile)infos[0];
    final String fileName = file.getName();
    final int prefixIndex = fileName.indexOf((int)'.');
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
}