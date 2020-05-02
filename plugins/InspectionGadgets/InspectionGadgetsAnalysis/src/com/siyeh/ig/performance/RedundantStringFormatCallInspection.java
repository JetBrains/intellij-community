// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Locale;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class RedundantStringFormatCallInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RemoveRedundantStringFormatVisitor(holder, isOnTheFly);
  }

  private static final class RemoveRedundantStringFormatVisitor extends JavaElementVisitor {

    private static final CallMatcher PRINTSTREAM_PRINTF = instanceCall(PrintStream.class.getName(), "printf")
      .parameterTypes(String.class.getName(), "java.lang.Object...");
    private static final CallMatcher PRINTSTREAM_PRINT = instanceCall(PrintStream.class.getName(), "print")
      .parameterTypes(String.class.getName());
    private static final CallMatcher PRINTSTREAM_PRINTLN = instanceCall(PrintStream.class.getName(), "println")
      .parameterTypes(String.class.getName());

    private static final CallMatcher STRING_FORMAT = staticCall(String.class.getName(), "format");

    private final CallMapper<ProblemDescriptor> myProcessors = new CallMapper<ProblemDescriptor>()
      .register(PRINTSTREAM_PRINTF, this::getRedundantPrintfProblem)
      .register(STRING_FORMAT, this::getRedundantStringFormatProblem);

    @NotNull private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;
    @NotNull private final InspectionManager myManager;

    private RemoveRedundantStringFormatVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
      myHolder = holder;
      myManager = myHolder.getManager();
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitMethodCallExpression(@NotNull final PsiMethodCallExpression call) {
      final ProblemDescriptor descriptor = myProcessors.mapFirst(call);
      if (descriptor != null) {
        myHolder.registerProblem(descriptor);
      }
    }

    @Nullable
    private ProblemDescriptor getRedundantPrintfProblem(@NotNull final PsiMethodCallExpression call) {
      final PsiExpressionList args = call.getArgumentList();
      if (args.getExpressionCount() != 1) return null;

      final PsiExpression formatValue = args.getExpressions()[0];
      if (containsNewlineToken(formatValue)) return null;

      final PsiElement method = call.getMethodExpression().getReferenceNameElement();
      if (method == null) return null;

      final TextRange textRange = new TextRange(method.getStartOffsetInParent(),
                                                method.getStartOffsetInParent() + method.getTextLength());
      return myManager.createProblemDescriptor(call, textRange,
                                               InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                               new ReplaceWithPrintFix());
    }

    @Nullable
    private ProblemDescriptor getRedundantStringFormatProblem(@NotNull final PsiMethodCallExpression call) {
      if (isStringFormatCallRedundant(call)) {
        return myManager.createProblemDescriptor(call, (TextRange)null,
                                                 InspectionGadgetsBundle.message("redundant.string.format.call.display.name"),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                                 new RemoveRedundantStringFormatFix());
      }
      final PsiMethodCallExpression printlnCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
      final boolean isPrintlnCall = PRINTSTREAM_PRINTLN.test(printlnCall);
      if (!isPrintlnCall) {
        if (!PRINTSTREAM_PRINT.test(printlnCall)) return null;

      }
      return myManager.createProblemDescriptor(call, (TextRange)null,
                                               InspectionGadgetsBundle.message("redundant.string.format.call.display.name"),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                               new StringFormatToPrintfQuickFix(isPrintlnCall));
    }

    @Contract(pure = true)
    private static boolean isStringFormatCallRedundant(@NotNull final PsiMethodCallExpression call) {
      final PsiExpressionList params = call.getArgumentList();
      if (params.getExpressionCount() == 1) {
        return !containsNewlineToken(params.getExpressions()[0]);
      }
      else if (params.getExpressionCount() == 2) {
        final PsiExpression firstArg = params.getExpressions()[0];
        if (firstArg.getType() == null || !firstArg.getType().equalsToText(Locale.class.getName())) return false;

        return !containsNewlineToken(params.getExpressions()[1]);
      }
      return false;
    }

    @Contract("null -> false")
    private static boolean containsNewlineToken(@Nullable final PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        final String expressionText = literalExpression.getText();
        return expressionText.contains("%n");
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.PLUS)) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (containsNewlineToken(operand)) {
            return true;
          }
        }
      }
      return false;
    }

    private static final class ReplaceWithPrintFix implements LocalQuickFix {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", "printf()", "print()");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiMethodCallExpression)) return;

        final PsiMethodCallExpression printStreamPrintfCall = (PsiMethodCallExpression)element;

        ExpressionUtils.bindCallTo(printStreamPrintfCall, "print");
      }
    }

    private static final class RemoveRedundantStringFormatFix implements LocalQuickFix {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.format.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull Project project,
                           @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiMethodCallExpression)) return;
        final PsiMethodCallExpression stringFormat = (PsiMethodCallExpression)element;
        final PsiElement parent = stringFormat.getParent();
        if (parent instanceof PsiExpressionList && ((PsiExpressionList)parent).getExpressionCount() == 1 && parent.getParent() instanceof PsiMethodCallExpression){
          final PsiMethodCallExpression printCall = (PsiMethodCallExpression)parent.getParent();
          final PsiExpression[] args = stringFormat.getArgumentList().getExpressions();
          if (args.length > 1) {
            new CommentTracker().deleteAndRestoreComments(args[0]);
          }
          new CommentTracker().replaceAndRestoreComments(printCall.getArgumentList(), stringFormat.getArgumentList());
        }
        else {
          final CommentTracker ct = new CommentTracker();
          final PsiExpression[] args = stringFormat.getArgumentList().getExpressions();
          final String expression = ct.text(args[args.length - 1]);
          ct.replaceAndRestoreComments(stringFormat, expression);
        }
      }
    }

    private static final class StringFormatToPrintfQuickFix implements LocalQuickFix {
      private final boolean myIsPrintlnCall;

      private StringFormatToPrintfQuickFix(boolean isPrintlnCall) {
        myIsPrintlnCall = isPrintlnCall;
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.format.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiMethodCallExpression)) return;

        final PsiMethodCallExpression stringFormatCall = (PsiMethodCallExpression)element;

        final PsiMethodCallExpression printlnCall = PsiTreeUtil.getParentOfType(stringFormatCall, PsiMethodCallExpression.class);
        if (printlnCall == null) return;

        final PsiExpressionList stringFormatArgs = stringFormatCall.getArgumentList();
        if (myIsPrintlnCall) {
          addNewlineToFormatValue(stringFormatArgs);
        }

        ExpressionUtils.bindCallTo(printlnCall, "printf");
        final PsiExpressionList printlnArgs = printlnCall.getArgumentList();
        new CommentTracker().replaceAndRestoreComments(printlnArgs, stringFormatArgs);
      }

      private static void addNewlineToFormatValue(@NotNull final PsiExpressionList stringFormatArgs) {
        if (stringFormatArgs.getExpressionCount() == 0) return;

        final PsiExpression formatValueArg = getArgWithFormatValue(stringFormatArgs);
        if (formatValueArg != null) {
          appendWithNewlineToken(formatValueArg);
        }
      }

      @Nullable
      @Contract(pure = true)
      private static PsiExpression getArgWithFormatValue(@NotNull final PsiExpressionList stringFormatArgs) {
        final PsiExpression firstFormatArg = stringFormatArgs.getExpressions()[0];
        final PsiType firstType = firstFormatArg.getType();

        if (firstType == null) return null;

        if (firstType.equalsToText(Locale.class.getName())) {
          if (stringFormatArgs.getExpressionCount() <= 1) return null;

          final PsiExpression secondFormatArg = stringFormatArgs.getExpressions()[1];
          final PsiType secondType = secondFormatArg.getType();
          if (secondType == null || !secondType.equalsToText(String.class.getName())) return null;

          return secondFormatArg;
        }
        else if (firstType.equalsToText(String.class.getName())) {
          return firstFormatArg;
        }
        return null;
      }

      private static void appendWithNewlineToken(@NotNull final PsiElement formatArg) {
        final String newLineToken = "%n";

        if (formatArg instanceof PsiLiteralExpression) {
          final PsiLiteralExpression replacement = PsiLiteralUtil.append((PsiLiteralExpression)formatArg, newLineToken);
          formatArg.replace(replacement);
        }
        else if (formatArg instanceof PsiPolyadicExpression){
          final PsiElement lastChild = formatArg.getLastChild();
          if (lastChild instanceof PsiLiteralExpression) {
            final PsiLiteralExpression replacement = PsiLiteralUtil.append((PsiLiteralExpression)lastChild, newLineToken);
            lastChild.replace(replacement);
          }
          else {
            final CommentTracker ct = new CommentTracker();
            final String text = String.format("%s + \"%s\"", ct.text(formatArg), newLineToken);
            ct.replaceAndRestoreComments(formatArg, text);
          }
        }
        else {
          final CommentTracker ct = new CommentTracker();
          final String text = String.format("(%s) + \"%s\"", ct.text(formatArg), newLineToken);
          ct.replaceAndRestoreComments(formatArg, text);
        }
      }

    }
  }
}
