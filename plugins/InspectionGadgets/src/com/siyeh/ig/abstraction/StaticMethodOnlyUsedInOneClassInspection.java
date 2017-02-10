/*
 * Copyright 2006-2016 Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticMethodOnlyUsedInOneClassInspection extends StaticMethodOnlyUsedInOneClassInspectionBase {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass usageClass = (PsiClass)infos[0];
    return new StaticMethodOnlyUsedInOneClassFix(usageClass);
  }

  private static class StaticMethodOnlyUsedInOneClassFix extends RefactoringInspectionGadgetsFix {

    private final SmartPsiElementPointer<PsiClass> usageClass;

    public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass) {
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
      this.usageClass = pointerManager.createSmartPsiElementPointer(usageClass);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.quickfix");
    }

    @NotNull
    @Override
    public RefactoringActionHandler getHandler() {
      return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    }

    @NotNull
    @Override
    public DataContext enhanceDataContext(DataContext context) {
      return SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), usageClass.getElement(), context);
    }
  }
}
