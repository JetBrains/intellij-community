/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ExtendsThreadInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getID() {
    return "ClassExplicitlyExtendsThread";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("extends.thread.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message("anonymous.extends.thread.problem.descriptor");
    } else {
      return InspectionGadgetsBundle.message("extends.thread.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExtendsThreadVisitor();
  }

  private static class ExtendsThreadVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      final String superclassName = superClass.getQualifiedName();
      if (!"java.lang.Thread".equals(superclassName)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
