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
package com.siyeh.ig;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionGadgetsFix implements LocalQuickFix {

  public static final InspectionGadgetsFix[] EMPTY_ARRAY = {};

  private boolean myOnTheFly = false;

  @Override
  public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement == null || !problemElement.isValid()) {
      return;
    }
    if (prepareForWriting() && !FileModificationService.getInstance().preparePsiElementsForWrite(problemElement)) {
      return;
    }
    try {
      doFix(project, descriptor);
    }
    catch (IncorrectOperationException e) {
      final Class<? extends InspectionGadgetsFix> aClass = getClass();
      final String className = aClass.getName();
      final Logger logger = Logger.getInstance(className);
      logger.error(e);
    }
  }

  protected boolean prepareForWriting() {
    return true;
  }

  protected abstract void doFix(Project project, ProblemDescriptor descriptor);

  protected static void deleteElement(@NotNull PsiElement element) {
    element.delete();
  }

  public final void setOnTheFly(boolean onTheFly) {
    myOnTheFly = onTheFly;
  }

  public final boolean isOnTheFly() {
    return myOnTheFly;
  }
}