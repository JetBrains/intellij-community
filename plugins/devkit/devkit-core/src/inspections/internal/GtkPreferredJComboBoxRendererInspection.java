/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

import javax.swing.*;

public class GtkPreferredJComboBoxRendererInspection extends DevKitInspectionBase {

  private static final String COMBO_BOX_CLASS_NAME = JComboBox.class.getName();
  private static final String[] RIGHT_RENDERER_CLASS_NAMES =
    {ListCellRendererWrapper.class.getName(), ColoredListCellRenderer.class.getName()};
  private static final String SETTER_METHOD_NAME = "setRenderer";

  private static final String MESSAGE =
    "Default ListCellRenderer implementations are known to cause UI artifacts under GTK+ look&feel, " +
    "so please use ListCellRendererWrapper or ColoredListCellRenderer instead.";

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(final PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        final Project project = expression.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

        final PsiElement target = expression.getMethodExpression().resolve();
        if (!(target instanceof PsiMethod)) return;
        final PsiMethod method = (PsiMethod)target;
        if (!SETTER_METHOD_NAME.equals(method.getName())) return;
        final PsiClass aClass = ((PsiMethod)target).getContainingClass();
        final PsiClass comboClass = facade.findClass(COMBO_BOX_CLASS_NAME, GlobalSearchScope.allScope(project));
        if (!InheritanceUtil.isInheritorOrSelf(aClass, comboClass, true)) return;

        final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (arguments.length != 1) return;
        final PsiType type = arguments[0].getType();
        if (!(type instanceof PsiClassType)) return;
        final PsiClass rendererClass = ((PsiClassType)type).resolve();
        for (String rightClassName : RIGHT_RENDERER_CLASS_NAMES) {
          final PsiClass rightClass = facade.findClass(rightClassName, GlobalSearchScope.allScope(project));
          if (InheritanceUtil.isInheritorOrSelf(rendererClass, rightClass, true)) return;
        }

        holder.registerProblem(expression, MESSAGE);
      }
    };
  }
}
