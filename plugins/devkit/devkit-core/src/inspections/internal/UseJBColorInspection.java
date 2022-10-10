// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBColorConstantQuickFix;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBColorQuickFix;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class UseJBColorInspection extends DevKitInspectionBase {

  private static final String AWT_COLOR_CLASS_NAME = Color.class.getName();
  private static final String JB_COLOR_CLASS_NAME = JBColor.class.getName();

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS =
    new Class[]{UCallExpression.class, UQualifiedReferenceExpression.class, USimpleNameReferenceExpression.class};

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        if (expression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          if (isAwtColorConstructor(expression) &&
              isJBColorClassAccessible(expression) &&
              !isUsedAsJBColorConstructorParameter(expression)) {
            PsiElement sourcePsi = expression.getSourcePsi();
            if (sourcePsi != null) {
              holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.awt.color.used"), new ConvertToJBColorQuickFix());
            }
          }
        }
        return super.visitCallExpression(expression);
      }

      private static boolean isAwtColorConstructor(@NotNull UCallExpression constructorCall) {
        return isColorTypeConstructor(constructorCall, AWT_COLOR_CLASS_NAME);
      }

      private static boolean isColorTypeConstructor(@NotNull UCallExpression constructorCall, @NotNull String colorClassName) {
        PsiMethod constructor = constructorCall.resolve();
        if (constructor != null) {
          PsiClass constructorClass = constructor.getContainingClass();
          if (constructorClass != null) {
            return colorClassName.equals(constructorClass.getQualifiedName());
          }
        }
        return false;
      }

      private static boolean isJBColorClassAccessible(@NotNull UElement uElement) {
        PsiElement checkedPlace = uElement.getSourcePsi();
        if (checkedPlace == null) return false;
        Project project = checkedPlace.getProject();
        PsiClass jbColorClass = JavaPsiFacade.getInstance(project).findClass(JB_COLOR_CLASS_NAME, checkedPlace.getResolveScope());
        return jbColorClass != null;
      }

      private static boolean isUsedAsJBColorConstructorParameter(@NotNull UExpression expression) {
        UCallExpression containingCall = UastContextKt.getUastParentOfType(expression.getSourcePsi(), UCallExpression.class, true);
        return containingCall != null &&
               containingCall.getKind() == UastCallKind.CONSTRUCTOR_CALL &&
               isJBColorConstructor(containingCall);
      }

      private static boolean isJBColorConstructor(@NotNull UCallExpression constructorCall) {
        return isColorTypeConstructor(constructorCall, JB_COLOR_CLASS_NAME);
      }

      @Override
      public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
        if (!(node.getUastParent() instanceof UImportStatement)) {
          inspectExpression(node);
        }
        return super.visitQualifiedReferenceExpression(node);
      }

      @Override
      public boolean visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression node) {
        inspectExpression(node);
        return super.visitSimpleNameReferenceExpression(node);
      }

      private void inspectExpression(@NotNull UReferenceExpression expression) {
        if (isAwtColorConstantReference(expression) &&
            isJBColorClassAccessible(expression) &&
            !isUsedAsJBColorConstructorParameter(expression)) {
          PsiElement sourcePsi = expression.getSourcePsi();
          if (sourcePsi != null) {
            holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.awt.color.used"), new ConvertToJBColorConstantQuickFix());
          }
        }
      }

      private static boolean isAwtColorConstantReference(@NotNull UReferenceExpression expression) {
        // avoid double warning for qualified and simple reference in Kotlin:
        if (expression.getUastParent() instanceof UQualifiedReferenceExpression) return false;
        PsiElement colorField = expression.resolve();
        if (colorField instanceof PsiField && ((PsiField)colorField).hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass colorClass = ((PsiField)colorField).getContainingClass();
          return colorClass != null && AWT_COLOR_CLASS_NAME.equals(colorClass.getQualifiedName());
        }
        return false;
      }
    }, HINTS);
  }
}
