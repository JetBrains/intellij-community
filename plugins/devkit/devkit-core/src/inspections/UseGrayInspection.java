// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToGrayQuickFix;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class UseGrayInspection extends DevKitUastInspectionBase {

  private static final String AWT_COLOR_CLASS_NAME = Color.class.getName();
  private static final String GRAY_CLASS_NAME = Gray.class.getName();

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class};

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        if (expression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          if (isAwtRgbColorConstructor(expression)) {
            Integer grayValue = getGrayValue(expression);
            if (grayValue != null) {
              PsiElement sourcePsi = expression.getSourcePsi();
              if (sourcePsi != null && grayClassAccessible(sourcePsi)) {
                holder.registerProblem(sourcePsi,
                                       DevKitBundle.message("inspections.use.gray.awt.color.used.name"),
                                       new ConvertToGrayQuickFix(grayValue));
              }
            }
          }
        }
        return super.visitCallExpression(expression);
      }
    }, HINTS);
  }

  private static boolean isAwtRgbColorConstructor(@NotNull UCallExpression constructorCall) {
    List<UExpression> constructorParams = constructorCall.getValueArguments();
    if (constructorParams.size() != 3) return false;
    PsiMethod constructor = constructorCall.resolve();
    if (constructor == null) return false;
    PsiClass constructorClass = constructor.getContainingClass();
    if (constructorClass == null) return false;
    return AWT_COLOR_CLASS_NAME.equals(constructorClass.getQualifiedName());
  }

  @Nullable
  private static Integer getGrayValue(@NotNull UCallExpression constructorCall) {
    List<UExpression> constructorParams = constructorCall.getValueArguments();
    UExpression redParam = constructorParams.get(0);
    Integer red = evaluateColorValue(redParam);
    if (red == null) return null;
    UExpression greenParam = constructorParams.get(1);
    UExpression blueParam = constructorParams.get(2);
    return 0 <= red && red < 256 && red.equals(evaluateColorValue(greenParam)) && red.equals(evaluateColorValue(blueParam)) ? red : null;
  }

  @Nullable
  private static Integer evaluateColorValue(@NotNull UExpression expression) {
    Object evaluatedExpression = expression.evaluate();
    if (evaluatedExpression instanceof Integer value) {
      return value;
    }
    return null;
  }

  private static boolean grayClassAccessible(@NotNull PsiElement checkedPlace) {
    return JavaPsiFacade.getInstance(checkedPlace.getProject()).findClass(GRAY_CLASS_NAME, checkedPlace.getResolveScope()) != null;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "InspectionUsingGrayColors";
  }
}
