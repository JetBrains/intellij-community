// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class RedundantClassCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher IS_INSTANCE =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "isInstance").parameterCount(1);
  private static final CallMatcher CAST =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "cast").parameterCount(1);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        boolean isInstance = IS_INSTANCE.test(call);
        boolean cast = CAST.test(call);
        if (isInstance || cast) {
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiClassObjectAccessExpression) {
            PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)qualifier).getOperand();
            PsiType classType = typeElement.getType();
            PsiExpression argument = call.getArgumentList().getExpressions()[0];
            PsiType argumentType = argument.getType();
            if (argumentType == null || !argumentType.isConvertibleFrom(classType)) {
              // will be a compilation error after replacement; skip this
              return;
            }
            LocalQuickFix fix = isInstance ? new ReplaceWithInstanceOfFix(typeElement) : new ReplaceWithCastFix(typeElement);
            holder.registerProblem(nameElement, InspectionGadgetsBundle.message("redundant.call.problem.descriptor"), fix);
          }
        }
      }
    };
  }

  private static abstract class ReplaceRedundantClassCallFix implements LocalQuickFix {
    final String myReplacement;

    ReplaceRedundantClassCallFix(String replacement) {
      myReplacement = replacement;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("replace.with", myReplacement);
    }

    @Override
    public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (arg == null) return;
      PsiClassObjectAccessExpression qualifier =
        tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()),
                PsiClassObjectAccessExpression.class);
      if (qualifier == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, createReplacement(ct.text(arg), ct.text(qualifier.getOperand())));
    }

    @NotNull
    abstract String createReplacement(String argText, String classText);
  }

  private static class ReplaceWithInstanceOfFix extends ReplaceRedundantClassCallFix {
    public ReplaceWithInstanceOfFix(@NotNull PsiTypeElement typeElement) {
      super("instanceof "+typeElement.getType().getPresentableText());
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'instanceof'";
    }

    @NotNull
    @Override
    String createReplacement(String argText, String classText) {
      return argText + " instanceof " + classText;
    }
  }

  private static class ReplaceWithCastFix extends ReplaceRedundantClassCallFix {
    public ReplaceWithCastFix(@NotNull PsiTypeElement typeElement) {
      super("("+typeElement.getType().getPresentableText()+")");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with cast";
    }

    @NotNull
    @Override
    String createReplacement(String argText, String classText) {
      return "("+classText+")"+argText;
    }
  }
}
