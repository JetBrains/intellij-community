// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
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
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        if (isAwtColorConstructor(expression) && isJBColorClassAccessible(expression) && !isUsedAsJBColorConstructorParameter(expression)) {
          holder.registerProblem(expression, DevKitBundle.message("inspections.awt.color.used"), new ConvertToJBColorQuickFix());
        }
      }

      private static boolean isAwtColorConstructor(@NotNull PsiNewExpression expression) {
        final PsiType type = expression.getType();
        final PsiExpressionList arguments = expression.getArgumentList();
        return type != null && arguments != null && type.equalsToText("java.awt.Color");
      }

      private static boolean isJBColorClassAccessible(@NotNull PsiElement checkedPlace) {
        final Project project = checkedPlace.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass jbColorClass = facade.findClass(JBColor.class.getName(), GlobalSearchScope.allScope(project));
        return jbColorClass != null && facade.getResolveHelper().isAccessible(jbColorClass, checkedPlace, jbColorClass);
      }

      private static boolean isUsedAsJBColorConstructorParameter(@NotNull PsiExpression expression) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiNewExpression) {
          final PsiType parentType = ((PsiNewExpression)parent.getParent()).getType();
          return parentType == null || JBColor.class.getName().equals(parentType.getCanonicalText());
        }
        return false;
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (isAwtColorConstantReference(expression) && !isUsedAsJBColorConstructorParameter(expression)) {
          holder.registerProblem(expression,
                                 DevKitBundle.message("inspections.awt.color.used"),
                                 new ConvertToJBColorConstantQuickFix(adjustColorConstantName(expression)));
        }
      }

      @NotNull
      private static @NonNls String adjustColorConstantName(@NotNull PsiReferenceExpression expression) {
        @NonNls String text = expression.getText();
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
        return StringUtil.toUpperCase(text);
      }

      private static boolean isAwtColorConstantReference(@NotNull PsiReferenceExpression expression) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) return false; // avoid resolving method names
        final PsiElement colorField = expression.resolve();
        if (colorField instanceof PsiField && ((PsiField)colorField).hasModifierProperty(PsiModifier.STATIC)) {
          final PsiClass colorClass = ((PsiField)colorField).getContainingClass();
          return colorClass != null && Color.class.getName().equals(colorClass.getQualifiedName());
        }
        return false;
      }
    };
  }
}
