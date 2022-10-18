// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

/**
 * @author Konstantin Bulenkov
 */
public class UseDPIAwareInsetsInspection extends DevKitInspectionBase {
  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        final ProblemDescriptor descriptor = checkNewExpression(expression, holder.getManager(), isOnTheFly);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
        super.visitNewExpression(expression);
      }
    };
  }

  @Nullable
  private static ProblemDescriptor checkNewExpression(PsiNewExpression expression, InspectionManager manager, boolean isOnTheFly) {
    final Project project = manager.getProject();
    final PsiType type = expression.getType();
    final PsiExpressionList arguments = expression.getArgumentList();
    if (type != null && arguments != null && type.equalsToText("java.awt.Insets")) {
      if (expression.getParent() instanceof PsiExpressionList) {
        PsiElement parent = expression.getParent();
        PsiElement superParent = parent.getParent();
        if (superParent instanceof PsiMethodCallExpression) {
          PsiType methodType = ((PsiMethodCallExpression)superParent).getType();
          if (methodType != null && methodType.equalsToText(JBInsets.class.getName())) {
            return null;
          }
        }
      }
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass jbuiClass = facade.findClass(JBUI.class.getName(), GlobalSearchScope.allScope(project));
      if (jbuiClass != null && facade.getResolveHelper().isAccessible(jbuiClass, expression, jbuiClass)) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiNewExpression) {
          final PsiType parentType = ((PsiNewExpression)parent.getParent()).getType();
          if (parentType == null || JBInsets.class.getName().equals(parentType.getCanonicalText())) return null;
        }
        if (arguments.getExpressionCount() == 4) {
          return manager.createProblemDescriptor(expression, DevKitBundle.message("inspections.use.dpi.aware.insets"),
                                                 new ConvertToJBInsetsQuickFix(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        }
      }
    }
    return null;
  }

  private static class ConvertToJBInsetsQuickFix implements LocalQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.insets.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiNewExpression newExpression = (PsiNewExpression)descriptor.getPsiElement();
      PsiExpressionList list = newExpression.getArgumentList();
      @NonNls String text;
      if (list != null && list.getExpressionCount() == 4) {
        String top = list.getExpressions()[0].getText();
        String left = list.getExpressions()[1].getText();
        String bottom = list.getExpressions()[2].getText();
        String right = list.getExpressions()[3].getText();

        if (isZero(top, left, bottom, right)) {
          text = "emptyInsets()";
        }
        else if (isZero(left, bottom, right)) {
          text = "insetsTop(" + top + ")";
        }
        else if (isZero(top, bottom, right)) {
          text = "insetsLeft(" + left + ")";
        }
        else if (isZero(top, left, right)) {
          text = "insetsBottom(" + bottom + ")";
        }
        else if (isZero(top, left, bottom)) {
          text = "insetsRight(" + right + ")";
        }
        else if (top.equals(left) && left.equals(bottom) && bottom.equals(right)) {
          text = "insets(" + top + ")";
        }
        else if (top.equals(bottom) && right.equals(left)) {
          text = String.format("insets(%s, %s)", top, left);
        }
        else {
          text = String.format("insets(%s, %s, %s, %s)", top, left, bottom, right);
        }

        text = JBUI.class.getName() + "." + text;

        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiExpression expression = factory.createExpressionFromText(text, newExpression.getContext());
        final PsiElement newElement = newExpression.replace(expression);
        final PsiElement el = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
        final int offset = el.getTextOffset() + el.getText().length() - 2;
        final Editor editor = PsiEditorUtil.findEditor(el);
        if (editor != null) {
          editor.getCaretModel().moveToOffset(offset);
        }
      }
    }

    private static boolean isZero(String... args) {
      if (args.length == 0) return false;
      for (String arg : args) {
        if (!"0".equals(arg)) return false;
      }
      return true;
    }
  }
}
