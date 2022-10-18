// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import javax.swing.border.EmptyBorder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class UseDPIAwareEmptyBorderInspection extends DevKitUastInspectionBase {
  private static final String SWING_EMPTY_BORDER_CLASS_NAME = EmptyBorder.class.getName();
  private static final String JB_UI_CLASS_NAME = JBUI.class.getName();
  private static final String JB_UI_BORDERS_CLASS_NAME = JB_UI_CLASS_NAME + ".Borders";
  private static final Integer ZERO = Integer.valueOf(0);

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS =
    new Class[]{UCallExpression.class, UQualifiedReferenceExpression.class, USimpleNameReferenceExpression.class};

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        UastCallKind expressionKind = expression.getKind();
        if (expressionKind == UastCallKind.METHOD_CALL) {
          if (isJBUIBordersEmptyMethodCall(expression) &&
              canBeSimplified(expression)) {
            PsiElement sourcePsi = expression.getSourcePsi();
            if (sourcePsi != null) {
              holder.registerProblem(sourcePsi,
                                     DevKitBundle.message("inspections.use.dpi.aware.empty.border.can.be.simplified"),
                                     new SimplifyJBUIEmptyBorderCreationQuickFix());
            }
          }
        }
        else if (expressionKind == UastCallKind.CONSTRUCTOR_CALL &&
                 isSwingEmptyBorderIntConstructor(expression) &&
                 isJBUIClassAccessible(expression)) {
          PsiElement sourcePsi = expression.getSourcePsi();
          if (sourcePsi != null) {
            holder.registerProblem(sourcePsi,
                                   DevKitBundle.message("inspections.use.dpi.aware.empty.border.not.dpi.aware"),
                                   new ConvertToJBUIBorderQuickFix());
          }
        }
        return super.visitCallExpression(expression);
      }
    }, HINTS);
  }

  private static boolean isJBUIBordersEmptyMethodCall(UCallExpression expression) {
    PsiMethod resolvedMethod = expression.resolve();
    if (resolvedMethod == null) return false;
    if (!resolvedMethod.getName().equals("empty")) return false;
    PsiClass containingClass = resolvedMethod.getContainingClass();
    if (containingClass == null) return false;
    return JB_UI_BORDERS_CLASS_NAME.equals(containingClass.getQualifiedName());
  }

  private static boolean canBeSimplified(UCallExpression expression) {
    int argumentCount = expression.getValueArgumentCount();
    if (!(argumentCount == 1 || argumentCount == 2 || argumentCount == 4)) {
      return false;
    }
    List<UExpression> params = expression.getValueArguments();
    for (UExpression param : params) {
      if (!PsiType.INT.equals(param.getExpressionType())) {
        return false;
      }
    }
    switch (params.size()) {
      case 1 -> {
        return isZero(params.get(0));
      }
      case 2 -> {
        return areSame(params.get(0), params.get(1));
      }
      case 4 -> {
        UExpression top = params.get(0);
        UExpression left = params.get(1);
        UExpression bottom = params.get(2);
        UExpression right = params.get(3);
        if (areSame(top, left, bottom, right) || (areSame(top, bottom) && areSame(left, right))) {
          return true;
        }
        int zeros = 0;
        for (UExpression param : params) {
          zeros += isZero(param) ? 1 : 0;
        }
        return zeros == 3;
      }
    }
    return false;
  }

  private static boolean areSame(UExpression... expressions) {
    if (expressions.length < 2) return false;
    Integer gold = evaluateIntegerValue(expressions[0]);
    if (gold == null) return false;
    for (UExpression expression : expressions) {
      if (!gold.equals(evaluateIntegerValue(expression))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isZero(UExpression... expressions) {
    if (expressions.length == 0) return false;
    for (UExpression expression : expressions) {
      if (!ZERO.equals(evaluateIntegerValue(expression))) return false;
    }
    return true;
  }

  @Nullable
  private static Integer evaluateIntegerValue(@NotNull UExpression expression) {
    Object evaluatedExpression = expression.evaluate();
    if (evaluatedExpression instanceof Integer value) {
      return value;
    }
    return null;
  }

  private static boolean isSwingEmptyBorderIntConstructor(@NotNull UCallExpression constructorCall) {
    if (constructorCall.getValueArgumentCount() != 4) return false;
    PsiMethod constructor = constructorCall.resolve();
    if (constructor == null) return false;
    PsiClass constructorClass = constructor.getContainingClass();
    if (constructorClass == null) return false;
    return SWING_EMPTY_BORDER_CLASS_NAME.equals(constructorClass.getQualifiedName());
  }


  private static boolean isJBUIClassAccessible(@NotNull UElement uElement) {
    PsiElement checkedPlace = uElement.getSourcePsi();
    if (checkedPlace == null) return false;
    Project project = checkedPlace.getProject();
    PsiClass jbuiClass = JavaPsiFacade.getInstance(project).findClass(JB_UI_CLASS_NAME, checkedPlace.getResolveScope());
    return jbuiClass != null;
  }

  private static abstract class AbstractEmptyBorderQuickFix implements LocalQuickFix {

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      UCallExpression uExpression = UastContextKt.toUElement(element, UCallExpression.class);
      if (uExpression == null) return;
      @NonNls String methodName = null;
      List<UExpression> paramValues = new ArrayList<>();
      switch (uExpression.getValueArgumentCount()) {
        case 1 -> methodName = "empty";
        case 2 -> {
          List<UExpression> params = uExpression.getValueArguments();
          UExpression topAndBottom = params.get(0);
          UExpression leftAndRight = params.get(1);
          if (isZero(topAndBottom, leftAndRight)) {
            methodName = "empty";
          }
          else if (areSame(topAndBottom, leftAndRight)) {
            methodName = "empty";
            paramValues.add(topAndBottom);
          }
        }
        case 4 -> {
          List<UExpression> params = uExpression.getValueArguments();
          UExpression top = params.get(0);
          UExpression left = params.get(1);
          UExpression bottom = params.get(2);
          UExpression right = params.get(3);
          if (isZero(top, left, bottom, right)) {
            methodName = "empty";
          }
          else if (isZero(left, bottom, right)) {
            methodName = "emptyTop";
            paramValues.add(top);
          }
          else if (isZero(top, bottom, right)) {
            methodName = "emptyLeft";
            paramValues.add(left);
          }
          else if (isZero(top, left, right)) {
            methodName = "emptyBottom";
            paramValues.add(bottom);
          }
          else if (isZero(top, left, bottom)) {
            methodName = "emptyRight";
            paramValues.add(right);
          }
          else if (areSame(top, left, bottom, right)) {
            methodName = "empty";
            paramValues.add(top);
          }
          else if (areSame(top, bottom) && areSame(right, left)) {
            methodName = "empty";
            paramValues.add(top);
            paramValues.add(left);
          }
          else {
            methodName = "empty";
            paramValues.add(top);
            paramValues.add(left);
            paramValues.add(bottom);
            paramValues.add(right);
          }
        }
      }
      if (methodName == null) return;

      UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(element.getLanguage());
      if (generationPlugin == null) return;
      UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
      UCallExpression emptyBorderFactoryMethodCall = pluginElementFactory.createCallExpression(
        getReceiverIfNeeded(pluginElementFactory, uExpression, element),
        methodName,
        paramValues,
        null,
        UastCallKind.METHOD_CALL,
        element
      );
      if (emptyBorderFactoryMethodCall == null) return;
      generationPlugin.replace(uExpression, emptyBorderFactoryMethodCall, UCallExpression.class);
    }
  }

  private static @Nullable UExpression getReceiverIfNeeded(UastElementFactory pluginElementFactory,
                                                           UCallExpression uCallExpression,
                                                           PsiElement context) {
    if (!uCallExpression.getLang().is(JavaLanguage.INSTANCE) && uCallExpression.getUastParent() instanceof UQualifiedReferenceExpression) {
      // workaround for IDEA-304078
      return null;
    }
    UExpression receiver = uCallExpression.getReceiver();
    return receiver != null ? receiver : pluginElementFactory.createQualifiedReference(JB_UI_BORDERS_CLASS_NAME, context);
  }

  private static class SimplifyJBUIEmptyBorderCreationQuickFix extends AbstractEmptyBorderQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.empty.border.simplify.fix.name");
    }
  }

  private static class ConvertToJBUIBorderQuickFix extends AbstractEmptyBorderQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.name");
    }
  }
}
