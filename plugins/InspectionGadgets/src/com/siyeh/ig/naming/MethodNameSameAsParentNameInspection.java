/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MethodNameSameAsParentNameInspection extends MethodInspection {

  private final RenameFix fix = new RenameFix();

  public String getGroupDisplayName() {
    return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MethodNameSameAsParentClassNameVisitor();
  }

  private static class MethodNameSameAsParentClassNameVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      // no call to super, so it doesn't drill down into inner classes
      if (method.isConstructor()) {
        return;
      }
      final String methodName = method.getName();
      if (methodName == null) {
        return;
      }

      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiClass parent = containingClass.getSuperClass();
      if (parent == null) {
        return;
      }
      final String parentName = parent.getName();
      if (parentName == null) {
        return;
      }
      if (!methodName.equals(parentName)) {
        return;
      }
      registerMethodError(method);
    }

  }
}
