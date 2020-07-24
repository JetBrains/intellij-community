// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.siyeh.ig.callMatcher.CallMatcher.exactInstanceCall;

public final class RedundantObjectArrayCreationInLoggingInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RedundantObjectArrayCreationVisitor(holder, isOnTheFly);
  }

  private static class RedundantObjectArrayCreationVisitor extends JavaElementVisitor {
    @NotNull private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;
    private static final String[] METHOD_NAMES = new String[]{"debug", "error", "info", "trace", "warn"};

    private static final CallMatcher LOGGER_MESSAGE = exactInstanceCall("org.slf4j.Logger", METHOD_NAMES)
      .parameterTypes(String.class.getName(), "java.lang.Object...");

    @Nullable
    private ProblemDescriptor getRedundantObjectArrayCreationProblem(@NotNull final PsiMethodCallExpression call) {
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList.getExpressionCount() != 2) return null;
      final PsiExpression objArray = PsiUtil.skipParenthesizedExprDown(argumentList.getExpressions()[1]);
      if (!(objArray instanceof PsiNewExpression)) return null;
      final PsiArrayInitializerExpression initializer = ((PsiNewExpression)objArray).getArrayInitializer();
      if (initializer == null) return null;
      final PsiExpression[] initializers = initializer.getInitializers();
      if (initializers.length > 2) return null;

      final String description = InspectionGadgetsBundle.message("redundant.object.array.creation.in.logging");
      final String actionMessage = getConvertedString(objArray, initializer);

      return myHolder.getManager().createProblemDescriptor(objArray, description,
                                                           new RemoveRedundantObjectArrayFix(actionMessage),
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
    }

    @NotNull
    private static String getConvertedString(@NotNull final PsiExpression objArray,
                                             @NotNull final PsiArrayInitializerExpression initializer) {
      if (ArrayUtil.isEmpty(initializer.getInitializers())) {
        return CommonQuickFixBundle.message("fix.remove.redundant", objArray.getText());
      }
      final String converted = Arrays.stream(initializer.getInitializers())
        .map(PsiElement::getText)
        .collect(Collectors.joining(", "));
      return CommonQuickFixBundle.message("fix.replace.x.with.y", objArray.getText(), converted);
    }

    private RedundantObjectArrayCreationVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitMethodCallExpression(@NotNull final PsiMethodCallExpression call) {
      if (!LOGGER_MESSAGE.test(call)) return;

      final ProblemDescriptor problem = getRedundantObjectArrayCreationProblem(call);
      if (problem != null) {
        myHolder.registerProblem(problem);
      }
    }

    private static final class RemoveRedundantObjectArrayFix implements LocalQuickFix {
      @NotNull private final String myName;

      private RemoveRedundantObjectArrayFix(@NotNull final String name) {
        myName = name;
      }

      @Override
      public @IntentionName @NotNull String getName() {
        return myName;
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.object.array.creation.in.logging.family.name");
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiNewExpression)) return;

        final PsiNewExpression arrayCreation = (PsiNewExpression)element;

        final PsiElement outermostParenExpression = outermostParenExpression(arrayCreation);
        final PsiElement parent = outermostParenExpression.getParent();
        if (!(parent instanceof PsiExpressionList)) return;

        final PsiArrayInitializerExpression initializer = arrayCreation.getArrayInitializer();
        final CommentTracker tracker = new CommentTracker();
        if (initializer != null && initializer.getInitializers().length > 0) {
          final PsiExpression[] initializers = initializer.getInitializers();
          final PsiElement firstNode = initializers[0];
          final PsiElement lastNode = getLastNode(initializers[initializers.length - 1]);
          parent.addRange(firstNode, lastNode);
          tracker.markRangeUnchanged(firstNode, lastNode);
        }

        tracker.deleteAndRestoreComments(outermostParenExpression);
      }

      @NotNull
      private static PsiElement getLastNode(@NotNull final PsiElement current) {
        @Nullable final PsiElement nextSibling = current.getNextSibling();
        if (nextSibling == null) return current;
        if (nextSibling.getNode().getElementType() == JavaTokenType.RBRACE) return current;
        return getLastNode(nextSibling);
      }

      @NotNull
      private static PsiElement outermostParenExpression(@NotNull final PsiElement element) {
        if (!(element.getParent() instanceof PsiParenthesizedExpression)) return element;
        return outermostParenExpression(element.getParent());
      }
    }
  }
}
