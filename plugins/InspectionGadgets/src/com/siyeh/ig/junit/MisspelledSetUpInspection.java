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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class MisspelledSetUpInspection extends MethodInspection {

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return new RenameFix("setUp");
  }

  public String getGroupDisplayName() {
    return GroupNames.JUNIT_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MisspelledSetUpVisitor();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class MisspelledSetUpVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super
      final PsiClass aClass = method.getContainingClass();
      @NonNls final String methodName = method.getName();
      if (!"setup".equals(methodName)) {
        return;
      }
      if (aClass == null) {
        return;
      }
      if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
        return;
      }

      registerMethodError(method);
    }


  }
}
