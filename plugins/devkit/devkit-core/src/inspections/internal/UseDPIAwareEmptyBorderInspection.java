// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.ui.JBUI;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

import javax.swing.border.EmptyBorder;

/**
 * @author Konstantin Bulenkov
 */
public class UseDPIAwareEmptyBorderInspection extends DevKitInspectionBase {
  private static final CallMatcher JB_UI_BORDERS_EMPTY_METHOD_CALL =
    CallMatcher.staticCall("com.intellij.util.ui.JBUI.Borders", "empty");

  public static final String JB_UI_CLASS_NAME = JBUI.class.getName();
  public static final String SWING_EMPTY_BORDER_CLASS_NAME = EmptyBorder.class.getName();

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        if (JB_UI_BORDERS_EMPTY_METHOD_CALL.test(expression) && canBeSimplified(expression)) {
          holder.registerProblem(expression,
                                 DevKitBundle.message("inspections.use.dpi.aware.empty.border.can.be.simplified"),
                                 new SimplifyJBUIEmptyBorderCreationQuickFix());
        }
        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (isSwingEmptyBorderConstructor(expression) && isJBUIClassAccessible(expression)) {
          holder.registerProblem(expression,
                                 DevKitBundle.message("inspections.use.dpi.aware.empty.border.not.dpi.aware"),
                                 new ConvertToJBUIBorderQuickFix());
        }
        super.visitNewExpression(expression);
      }
    };
  }

  private static boolean canBeSimplified(PsiMethodCallExpression expression) {
    PsiType[] types = expression.getArgumentList().getExpressionTypes();
    if (!(types.length == 1 || types.length == 2 || types.length == 4)) {
      return false;
    }
    for (PsiType type : types) {
      if (!PsiType.INT.equals(type)) {
        return false;
      }
    }

    PsiExpression[] params = expression.getArgumentList().getExpressions();
    if (params.length == 1) {
      return params[0].textMatches("0");
    }
    else if (params.length == 2) {
      return areSame(params);
    }
    else if (params.length == 4) {
      if (areSame(params) || (areSame(params[0], params[2]) && areSame(params[1], params[3]))) {
        return true;
      }
      int zeros = 0;
      for (PsiExpression param : params) {
        zeros += isZero(param.getText()) ? 1 : 0;
      }
      return zeros == 3;
    }
    return false;
  }

  private static boolean areSame(PsiExpression... params) {
    if (params.length < 2) return false;

    String gold = params[0].getText();
    for (int i = 1; i < params.length; i++) {
      if (!params[i].textMatches(gold)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isZero(String... args) {
    if (args.length == 0) return false;
    for (String arg : args) {
      if (!"0".equals(arg)) return false;
    }
    return true;
  }

  private static boolean isSwingEmptyBorderConstructor(@NotNull PsiNewExpression expression) {
    PsiExpressionList arguments = expression.getArgumentList();
    if (arguments != null && arguments.getExpressionCount() != 4) return false;
    PsiType type = expression.getType();
    return type != null && type.equalsToText(SWING_EMPTY_BORDER_CLASS_NAME);
  }

  private static boolean isJBUIClassAccessible(@NotNull PsiElement checkedPlace) {
    Project project = checkedPlace.getProject();
    PsiClass jbuiClass = JavaPsiFacade.getInstance(project).findClass(JB_UI_CLASS_NAME, checkedPlace.getResolveScope());
    return jbuiClass != null;
  }

  private static abstract class AbstractEmptyBorderQuickFix implements LocalQuickFix {

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiCall newExpression = (PsiCall)descriptor.getPsiElement();
      PsiExpressionList list = newExpression.getArgumentList();
      if (list == null) return;
      @NonNls String text = null;
      switch (list.getExpressionCount()) {
        case 1 -> text = "empty()";
        case 2 -> {
          String topAndBottom = list.getExpressions()[0].getText();
          String leftAndRight = list.getExpressions()[1].getText();
          if (isZero(topAndBottom, leftAndRight)) {
            text = "empty()";
          }
          else if (topAndBottom.equals(leftAndRight)) {
            text = "empty(" + topAndBottom + ")";
          }
        }
        case 4 -> {
          String top = list.getExpressions()[0].getText();
          String left = list.getExpressions()[1].getText();
          String bottom = list.getExpressions()[2].getText();
          String right = list.getExpressions()[3].getText();
          if (isZero(top, left, bottom, right)) {
            text = "empty()";
          }
          else if (isZero(left, bottom, right)) {
            text = "emptyTop(" + top + ")";
          }
          else if (isZero(top, bottom, right)) {
            text = "emptyLeft(" + left + ")";
          }
          else if (isZero(top, left, right)) {
            text = "emptyBottom(" + bottom + ")";
          }
          else if (isZero(top, left, bottom)) {
            text = "emptyRight(" + right + ")";
          }
          else if (top.equals(left) && left.equals(bottom) && bottom.equals(right)) {
            text = "empty(" + top + ")";
          }
          else if (top.equals(bottom) && right.equals(left)) {
            text = String.format("empty(%s, %s)", top, left);
          }
          else {
            text = String.format("empty(%s, %s, %s, %s)", top, left, bottom, right);
          }
        }
      }
      if (text == null) return;

      text = JB_UI_CLASS_NAME + ".Borders." + text;

      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      PsiExpression expression = factory.createExpressionFromText(text, newExpression.getContext());
      PsiElement newElement = newExpression.replace(expression);
      PsiElement el = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
      int offset = el.getTextOffset() + el.getText().length() - 2;
      Editor editor = PsiEditorUtil.findEditor(el);
      if (editor != null) {
        editor.getCaretModel().moveToOffset(offset);
      }
    }
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
