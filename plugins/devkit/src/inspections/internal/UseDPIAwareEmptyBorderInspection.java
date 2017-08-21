/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBBorderQuickFix;

/**
 * @author Konstantin Bulenkov
 */
public class UseDPIAwareEmptyBorderInspection extends DevKitInspectionBase {
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final ProblemDescriptor descriptor = checkMethodCallCanBeSimplified(expression, holder.getManager(), isOnTheFly);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final ProblemDescriptor descriptor = checkNewExpression(expression, holder.getManager(), isOnTheFly);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
        super.visitNewExpression(expression);
      }
    };
  }

  private static ProblemDescriptor checkMethodCallCanBeSimplified(PsiMethodCallExpression expression,
                                                                  InspectionManager manager,
                                                                  boolean isOnTheFly) {
    String text = expression.getText();
    if (text.contains("empty(")) {
      PsiType type = expression.getType();
      PsiType[] arguments = expression.getTypeArguments();
      if (type != null
          && JBEmptyBorder.class.getName().equals(type.getCanonicalText())
          && ConvertToJBBorderQuickFix.canSimplify(expression)) {

          return manager.createProblemDescriptor(expression, "Simplify", new ConvertToJBBorderQuickFix("Simplify"),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);

      }
    }
    return null;
  }

  @Nullable
  private static ProblemDescriptor checkNewExpression(PsiNewExpression expression, InspectionManager manager, boolean isOnTheFly) {
    final Project project = manager.getProject();
    final PsiType type = expression.getType();
    final PsiExpressionList arguments = expression.getArgumentList();
    if (type != null && arguments != null && type.equalsToText("javax.swing.border.EmptyBorder")) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass jbuiClass = facade.findClass(JBUI.class.getName(), GlobalSearchScope.allScope(project));
      if (jbuiClass != null && facade.getResolveHelper().isAccessible(jbuiClass, expression, jbuiClass)) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiNewExpression) {
          final PsiType parentType = ((PsiNewExpression)parent.getParent()).getType();
          if (parentType == null || JBEmptyBorder.class.getName().equals(parentType.getCanonicalText())) return null;
        }
        if (arguments.getExpressions().length == 4) {
          return manager.createProblemDescriptor(expression, "Replace with JBUI.Borders.empty(...)", new ConvertToJBBorderQuickFix(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        }
      }
    }
    return null;
  }

}
