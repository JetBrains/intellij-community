/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PublicConstructorInspection extends PublicConstructorInspectionBase {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceConstructorWithFactoryMethodFix();
  }

  private static class ReplaceConstructorWithFactoryMethodFix extends RefactoringInspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("public.constructor.quickfix");
    }

    @NotNull
    @Override
    public RefactoringActionHandler getHandler() {
      return JavaRefactoringActionHandlerFactory.getInstance().createReplaceConstructorWithFactoryHandler();
    }
  }
}
