// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Locale;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class RedundantStringFormatCallInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RemoveRedundantStringFormatVisitor(holder, isOnTheFly, HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile()));
  }

  private static final class RemoveRedundantStringFormatVisitor extends JavaElementVisitor {

    private static final CallMatcher PRINTSTREAM_PRINTF = instanceCall(PrintStream.class.getName(), "printf")
      .parameterTypes(String.class.getName(), "java.lang.Object...");
    private static final CallMatcher PRINTSTREAM_PRINT = instanceCall(PrintStream.class.getName(), "print")
      .parameterTypes(String.class.getName());
    private static final CallMatcher PRINTSTREAM_PRINTLN = instanceCall(PrintStream.class.getName(), "println")
      .parameterTypes(String.class.getName());

    private static final CallMatcher STRING_FORMAT = staticCall(String.class.getName(), "format");
    private static final CallMatcher STRING_FORMATTED = instanceCall(String.class.getName(), "formatted")
      .parameterTypes("java.lang.Object...");

    @NotNull
    private final CallMapper<ProblemDescriptor> myProcessors;

    @NotNull private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;
    @NotNull private final InspectionManager myManager;

    private RemoveRedundantStringFormatVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, boolean withTextBlocks) {
      myHolder = holder;
      myManager = myHolder.getManager();
      myIsOnTheFly = isOnTheFly;
      myProcessors = new CallMapper<ProblemDescriptor>()
        .register(PRINTSTREAM_PRINTF, this::getRedundantPrintfProblem)
        .register(STRING_FORMAT, this::getRedundantStringFormatProblem);
      if (withTextBlocks) {
        myProcessors.register(STRING_FORMATTED, this::getRedundantFormattedProblem);
      }
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

      final PsiElement methodNameReference = call.getMethodExpression().getReferenceNameElement();
      if (methodNameReference == null) return null;

      final PsiExpression formatValue = args.getExpressions()[0];
      if (containsNewlineToken(formatValue)) return null;

      return myManager.createProblemDescriptor(methodNameReference,
                                               InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                               new ReplaceWithPrintFix(),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
    }

    @Nullable
    private ProblemDescriptor getRedundantStringFormatProblem(@NotNull final PsiMethodCallExpression call) {
      final PsiElement methodNameReference = call.getMethodExpression().getReferenceNameElement();
      if (methodNameReference == null) return null;

      if (isStringFormatCallRedundant(call)) {
        return myManager.createProblemDescriptor(methodNameReference,
                                                 InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                                 new RemoveRedundantStringFormatFix(),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
      }
      final PsiMethodCallExpression printlnCall = getDirectParentMethod(call);
      final boolean isPrintlnCall = PRINTSTREAM_PRINTLN.test(printlnCall);
      if (!isPrintlnCall) {
        if (!PRINTSTREAM_PRINT.test(printlnCall)) return null;
      }

      return myManager.createProblemDescriptor(methodNameReference,
                                               InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                               new ReplaceStringFormatWithPrintfFix(isPrintlnCall),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
    }

    @Nullable
    private ProblemDescriptor getRedundantFormattedProblem(@NotNull final PsiMethodCallExpression call) {
      final PsiElement methodNameReference = call.getMethodExpression().getReferenceNameElement();
      if (methodNameReference == null) return null;

      if (call.getArgumentList().getExpressionCount() == 0 && !containsNewlineToken(call.getMethodExpression().getQualifierExpression())) {
          return myManager.createProblemDescriptor(methodNameReference,
                                                   InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                                   new RemoveRedundantStringFormattedFix(),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
      }
      final PsiMethodCallExpression printlnCall = getDirectParentMethod(call);
      final boolean isPrintlnCall = PRINTSTREAM_PRINTLN.test(printlnCall);
      if (!isPrintlnCall && !PRINTSTREAM_PRINT.test(printlnCall)) return null;

      return myManager.createProblemDescriptor(methodNameReference,
                                               InspectionGadgetsBundle.message("redundant.call.problem.descriptor"),
                                               new ReplaceStringFormattedWithPrintfFix(isPrintlnCall),
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
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
    private static boolean containsNewlineToken(@Nullable final PsiExpression expr) {
      if (expr == null) {
        return false;
      }
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(expr);
      if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        final String expressionText = literalExpression.getText();
        return expressionText.contains("%n");
      }
      if (expression instanceof PsiCallExpression) {
        final PsiCallExpression callExpression = (PsiCallExpression)expression;
        final PsiMethod method = callExpression.resolveMethod();
        if (method != null) {
          final PsiClass aClass = method.getContainingClass();
          if (aClass != null && CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName())) {
            final PsiExpressionList argumentList = callExpression.getArgumentList();
            if (argumentList != null) {
              final PsiExpression[] arguments = argumentList.getExpressions();
              for (PsiExpression argument : arguments) {
                if (containsNewlineToken(argument)) {
                  return true;
                }
              }
              return false;
            }
          }
        }
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
        return false;
      }
      // could not be evaluated at compile tie, so possibly contains %n
      return true;
    }

    private static final class ReplaceWithPrintFix implements LocalQuickFix {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", "printf()", "print()");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiMethodCallExpression printStreamPrintfCall = getDirectParentMethod(descriptor.getPsiElement());
        if (printStreamPrintfCall == null) return;

        ExpressionUtils.bindCallTo(printStreamPrintfCall, "print");

        final PsiExpressionList argumentList = printStreamPrintfCall.getArgumentList();
        final PsiExpression arg = PsiUtil.skipParenthesizedExprDown(argumentList.getExpressions()[0]);
        if (arg instanceof PsiMethodCallExpression && STRING_FORMAT.matches(arg) && isStringFormatCallRedundant((PsiMethodCallExpression)arg)) {
          removeRedundantStringFormatCall((PsiMethodCallExpression)arg);
        }
      }
    }

    private static final class RemoveRedundantStringFormatFix implements LocalQuickFix {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.format.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull final Project project,
                           @NotNull final ProblemDescriptor descriptor) {
        final PsiMethodCallExpression stringFormat = getDirectParentMethod(descriptor.getPsiElement());
        if (stringFormat != null) {
          removeRedundantStringFormatCall(stringFormat);
        }

      }
    }

    private static final class ReplaceStringFormatWithPrintfFix implements LocalQuickFix {
      private final boolean myIsPrintlnCall;

      private ReplaceStringFormatWithPrintfFix(boolean isPrintlnCall) {
        myIsPrintlnCall = isPrintlnCall;
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.format.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiMethodCallExpression stringFormatCall = getDirectParentMethod(descriptor.getPsiElement());
        if (stringFormatCall == null) return;

        final PsiMethodCallExpression printlnCall = getDirectParentMethod(stringFormatCall);
        if (printlnCall == null) return;

        final PsiExpressionList stringFormatArgs = stringFormatCall.getArgumentList();
        if (myIsPrintlnCall) {
          addNewlineToFormatValue(stringFormatArgs);
        }

        ExpressionUtils.bindCallTo(printlnCall, "printf");
        final PsiExpressionList printlnArgs = printlnCall.getArgumentList();
        new CommentTracker().replaceAndRestoreComments(printlnArgs, stringFormatArgs);
      }
    }

    private static final class RemoveRedundantStringFormattedFix implements LocalQuickFix {
      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.formatted.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiMethodCallExpression stringFormattedCall = getDirectParentMethod(descriptor.getPsiElement());
        if (stringFormattedCall == null) return;

        final PsiExpression expression = stringFormattedCall.getMethodExpression().getQualifierExpression();
        if (expression != null) {
          new CommentTracker().replaceAndRestoreComments(stringFormattedCall, expression);
        }
      }
    }

    private static final class ReplaceStringFormattedWithPrintfFix implements LocalQuickFix {
      private final boolean myIsPrintlnCall;

      private ReplaceStringFormattedWithPrintfFix(boolean isPrintlnCall) {
        myIsPrintlnCall = isPrintlnCall;
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("redundant.string.formatted.call.quickfix");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiMethodCallExpression stringFormattedCall = getDirectParentMethod(descriptor.getPsiElement());
        if (stringFormattedCall == null) return;

        final PsiMethodCallExpression printlnCall = PsiTreeUtil.getParentOfType(stringFormattedCall, PsiMethodCallExpression.class);
        if (printlnCall == null) return;

        final PsiExpression textBlock = stringFormattedCall.getMethodExpression().getQualifierExpression();
        if (textBlock == null) return;

        final PsiExpressionList formattedArgs = stringFormattedCall.getArgumentList();
        final CommentTracker ct = new CommentTracker();
        final PsiElement element = formattedArgs.getExpressionCount() == 0
                                   ? formattedArgs.add(ct.markUnchanged(textBlock))
                                   : formattedArgs.addBefore(ct.markUnchanged(textBlock), formattedArgs.getExpressions()[0]);
        if (myIsPrintlnCall ) {
          appendWithNewlineToken((PsiExpression)element);
        }

        ct.replaceAndRestoreComments(printlnCall.getArgumentList(), formattedArgs);
        ExpressionUtils.bindCallTo(printlnCall, "printf");
      }
    }

    @Nullable
    private static PsiMethodCallExpression getDirectParentMethod(@Nullable final PsiElement methodName) {
      if (methodName == null || methodName.getParent() == null) return null;

      if (!(methodName.getParent().getParent() instanceof PsiMethodCallExpression)) return null;

      return (PsiMethodCallExpression)methodName.getParent().getParent();
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
      if (firstFormatArg == null) return null;
      final PsiType firstType = firstFormatArg.getType();

      if (firstType == null) return null;

      if (firstType.equalsToText(Locale.class.getName())) {
        if (stringFormatArgs.getExpressionCount() <= 1) return null;

        final PsiExpression secondFormatArg = stringFormatArgs.getExpressions()[1];
        if (secondFormatArg == null) return null;

        final PsiType secondType = secondFormatArg.getType();
        if (secondType == null || !secondType.equalsToText(String.class.getName())) return null;

        return secondFormatArg;
      }
      else if (firstType.equalsToText(String.class.getName())) {
        return firstFormatArg;
      }
      return null;
    }

    private static void appendWithNewlineToken(@NotNull final PsiExpression expr) {
      final PsiElement formatArg = PsiUtil.skipParenthesizedExprDown(expr);
      if (formatArg == null) return;

      final @NonNls String newLineToken = "%n";

      if (formatArg instanceof PsiLiteralExpression) {
        final PsiLiteralExpression replacement = joinWithNewlineToken((PsiLiteralExpression)formatArg);
        formatArg.replace(replacement);
      }
      else if (formatArg instanceof PsiPolyadicExpression){
        final PsiElement lastChild = PsiUtil.skipParenthesizedExprDown((PsiExpression)formatArg.getLastChild());
        if (lastChild instanceof PsiLiteralExpression) {
          final PsiLiteralExpression replacement = joinWithNewlineToken((PsiLiteralExpression)lastChild);
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

    @Contract(value = "null -> null; !null -> !null", pure = true)
    private static PsiLiteralExpression joinWithNewlineToken(@Nullable final PsiLiteralExpression expression) {
      if (expression == null) return null;

      final Object value = expression.getValue();
      if (value == null) return expression;

      final @NonNls StringBuilder newExpression = new StringBuilder();

      final String leftText = value.toString();
      if (expression.isTextBlock()) {
        final String indent = StringUtil.repeat(" ", PsiLiteralUtil.getTextBlockIndent(expression));
        newExpression.append("\"\"\"").append('\n').append(indent);
        newExpression.append(leftText.replace("\n", "\n" + indent));
        newExpression.append("%n");
        newExpression.append("\"\"\"");
      }
      else {
        newExpression.append('"');
        newExpression.append(StringUtil.escapeStringCharacters(leftText));
        newExpression.append("%n");
        newExpression.append('"');
      }

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
      return (PsiLiteralExpression)factory.createExpressionFromText(newExpression.toString(), null);
    }

    private static void removeRedundantStringFormatCall(@NotNull PsiMethodCallExpression stringFormat) {
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(stringFormat.getParent());
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
        final PsiExpression element = PsiUtil.skipParenthesizedExprDown(args[args.length - 1]);
        if (element == null) return;
        ct.replaceAndRestoreComments(stringFormat, element);
      }
    }
  }
}
