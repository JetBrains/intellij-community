/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InspectionGadgetsFix implements LocalQuickFix {

  public static final InspectionGadgetsFix[] EMPTY_ARRAY = {};
  private static final Logger LOG = Logger.getInstance("#com.siyeh.ig.InspectionGadgetsFix");

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

  protected static String getElementText(@NotNull PsiElement element, @Nullable PsiElement elementToReplace, @Nullable String replacement) {
    final StringBuilder out = new StringBuilder();
    getElementText(element, elementToReplace, replacement, out);
    return out.toString();
  }

  private static void getElementText(@NotNull PsiElement element, @Nullable PsiElement elementToReplace,
                                     @Nullable String replacement, @NotNull StringBuilder out) {
    if (element.equals(elementToReplace)) {
      out.append(replacement);
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      out.append(element.getText());
      return;
    }
    for (PsiElement child : children) {
      getElementText(child, elementToReplace, replacement, out);
    }
  }

  public final void setOnTheFly(boolean onTheFly) {
    myOnTheFly = onTheFly;
  }

  public final boolean isOnTheFly() {
    return myOnTheFly;
  }
}