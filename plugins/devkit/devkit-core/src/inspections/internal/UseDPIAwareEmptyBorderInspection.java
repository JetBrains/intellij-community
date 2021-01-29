// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToJBBorderQuickFix;

/**
 * @author Konstantin Bulenkov
 */
public class UseDPIAwareEmptyBorderInspection extends DevKitInspectionBase {
  private static final CallMatcher JBUI_BORDERS_EMPTY = 
    CallMatcher.staticCall("com.intellij.util.ui.JBUI.Borders", "empty");
  
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
    if (!JBUI_BORDERS_EMPTY.test(expression)) return null;
    if (ConvertToJBBorderQuickFix.canSimplify(expression)) {
      return manager.createProblemDescriptor(expression,
                                             DevKitBundle.message("inspections.use.dpi.aware.empty.border.simplify"),
                                             new ConvertToJBBorderQuickFix() {
                                               @Override
                                               public @IntentionFamilyName @NotNull String getFamilyName() {
                                                 return DevKitBundle.message("inspections.use.dpi.aware.empty.border.family.name");
                                               }
                                             },
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
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
        if (arguments.getExpressionCount() == 4) {
          return manager.createProblemDescriptor(expression, DevKitBundle.message("inspections.use.dpi.aware.empty.border.replace"),
                                                 new ConvertToJBBorderQuickFix(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        }
      }
    }
    return null;
  }
}
