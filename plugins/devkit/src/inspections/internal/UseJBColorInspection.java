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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBColorConstantQuickFix;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBColorQuickFix;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class UseJBColorInspection extends DevKitInspectionBase {
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final ProblemDescriptor descriptor = checkNewExpression(expression, holder.getManager(), isOnTheFly);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
        super.visitNewExpression(expression);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          //do not resolve method names
          return;
        }
        final PsiElement colorField = expression.resolve();
        if (colorField instanceof PsiField && ((PsiField)colorField).hasModifierProperty(PsiModifier.STATIC)) {
          final PsiClass colorClass = ((PsiField)colorField).getContainingClass();
          if (colorClass != null && Color.class.getName().equals(colorClass.getQualifiedName())) {
            String text = expression.getText();
            if (text.contains(".")) {
              text = text.substring(text.lastIndexOf('.'));
            }
            text = StringUtil.trimStart(text, ".");
            if (text.equalsIgnoreCase("lightGray")) {
              text = "LIGHT_GRAY";
            }
            else if (text.equalsIgnoreCase("darkGray")) {
              text = "DARK_GRAY";
            }
            final ProblemDescriptor descriptor = holder.getManager()
              .createProblemDescriptor(expression, "Change to JBColor." + text.toUpperCase(),
                                       new ConvertToJBColorConstantQuickFix(text.toUpperCase()),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
            holder.registerProblem(descriptor);
          }
        }
      }
    };
  }

  @Nullable
  private static ProblemDescriptor checkNewExpression(PsiNewExpression expression, InspectionManager manager, boolean isOnTheFly) {
    final Project project = manager.getProject();
    final PsiType type = expression.getType();
    final PsiExpressionList arguments = expression.getArgumentList();
    if (type != null && arguments != null && type.equalsToText("java.awt.Color")) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass jbColorClass = facade.findClass(JBColor.class.getName(), GlobalSearchScope.allScope(project));
      if (jbColorClass != null && facade.getResolveHelper().isAccessible(jbColorClass, expression, jbColorClass)) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiNewExpression) {
          final PsiType parentType = ((PsiNewExpression)parent.getParent()).getType();
          if (parentType == null || JBColor.class.getName().equals(parentType.getCanonicalText())) return null;
        }
        return manager.createProblemDescriptor(expression, "Replace with JBColor", new ConvertToJBColorQuickFix(),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
      }
    }
    return null;
  }

}
