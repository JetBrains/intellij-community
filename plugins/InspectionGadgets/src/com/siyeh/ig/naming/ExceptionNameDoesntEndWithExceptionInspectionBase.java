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
package com.siyeh.ig.naming;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExceptionNameDoesntEndWithExceptionInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getID() {
    return "ExceptionClassNameDoesntEndWithException";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "exception.name.doesnt.end.with.exception.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "exception.name.doesnt.end.with.exception.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionNameDoesntEndWithExceptionVisitor();
  }

  private static class ExceptionNameDoesntEndWithExceptionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      @NonNls final String exception = "Exception";
      if (className.endsWith(exception)) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass,
                                       CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}
