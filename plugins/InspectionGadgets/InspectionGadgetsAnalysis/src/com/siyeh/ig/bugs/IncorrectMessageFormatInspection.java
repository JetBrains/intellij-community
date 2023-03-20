// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.bugs.message.MessageFormatUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class IncorrectMessageFormatInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher PATTERN_METHODS = anyOf(
    staticCall("java.text.MessageFormat", "format").parameterCount(2)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (ConstructionUtils.isReferenceTo(expression.getClassReference(), "java.text.MessageFormat")) {
          PsiMethod method = expression.resolveMethod();
          if (method == null || !method.isConstructor()) {
            return;
          }

          PsiParameterList parameterList = method.getParameterList();
          if (parameterList.getParametersCount() < 1) {
            return;
          }
          PsiParameter[] parameters = parameterList.getParameters();
          if (parameters.length < 1 || parameters[0] == null) {
            return;
          }
          PsiType type = parameters[0].getType();
          if (!CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText())) {
            return;
          }
          PsiExpressionList argumentList = expression.getArgumentList();
          if (argumentList == null) {
            return;
          }
          checkStringFormatAndGetIndexes(argumentList.getExpressions()[0]);
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (PATTERN_METHODS.test(call)) {
          List<MessageFormatUtil.MessageFormatPlaceholder> indexes =
            checkStringFormatAndGetIndexes(call.getArgumentList().getExpressions()[0]);
          if (indexes != null) {
            checkIndexes(call, indexes);
          }
        }
      }

      private void checkIndexes(@NotNull PsiMethodCallExpression call,
                                @NotNull List<MessageFormatUtil.MessageFormatPlaceholder> indexes) {
        int count = call.getArgumentList().getExpressionCount();
        int argumentNumber = count - 1;
        List<Integer> notFoundArguments = new ArrayList<>();
        Set<Integer> usedArgumentIndexes = new HashSet<>();
        for (MessageFormatUtil.MessageFormatPlaceholder index : indexes) {
          if (index.index() >= argumentNumber) {
            notFoundArguments.add(index.index());
          }
          usedArgumentIndexes.add(index.index());
        }
        List<Integer> notUsedArguments = new ArrayList<>();
        for (int i = 0; i < argumentNumber; i++) {
          if (!usedArgumentIndexes.contains(i)) {
            notUsedArguments.add(i);
          }
        }
        if (notFoundArguments.isEmpty() && notUsedArguments.isEmpty()) {
          return;
        }

        PsiIdentifier[] identifiers = PsiTreeUtil.getChildrenOfType(call.getMethodExpression(), PsiIdentifier.class);
        if (identifiers == null || identifiers.length != 1) {
          return;
        }
        PsiIdentifier identifier = identifiers[0];
        if (!notFoundArguments.isEmpty()) {
          if (notFoundArguments.size() == 1) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.incorrect.message.format.not.found.argument",
                                                                               notFoundArguments.iterator().next()));
          }
          else {
            StringJoiner joiner = new StringJoiner(",");
            for (Integer notFoundArgument : notFoundArguments) {
              joiner.add(notFoundArgument.toString());
            }
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.incorrect.message.format.not.found.arguments",
                                                                               joiner.toString()));
          }
        }

        if (!notUsedArguments.isEmpty()) {
          PsiExpression[] expressions = call.getArgumentList().getExpressions();
          for (Integer notUsedArgument : notUsedArguments) {
            int expressionIndex = notUsedArgument + 1;
            PsiExpression expression = expressions[expressionIndex];
            if (expression == null) continue;
            holder.registerProblem(expression,
                                   InspectionGadgetsBundle.message("inspection.incorrect.message.format.not.used.argument",
                                                                   notUsedArgument));
          }
        }
      }

      @Nullable
      private List<MessageFormatUtil.MessageFormatPlaceholder> checkStringFormatAndGetIndexes(@Nullable PsiExpression expression) {
        if (expression == null) {
          return null;
        }
        boolean immediatePattern = true;
        String pattern = null;

        if (expression instanceof PsiLiteralExpression literalExpression) {
          pattern = literalExpression.getText();
        }

        if (pattern == null &&
            expression instanceof PsiReferenceExpression referenceExpression &&
            referenceExpression.resolve() instanceof PsiVariable variable &&
            variable.hasModifierProperty(PsiModifier.FINAL)) {
          PsiExpression initializer = variable.getInitializer();
          if (initializer instanceof PsiLiteralExpression literalExpression) {
            pattern = literalExpression.getText();
            expression = initializer;
          }
        }
        if (pattern == null) {
          immediatePattern = false;
          CommonDataflow.DataflowResult dataflowResult = CommonDataflow.getDataflowResult(expression);
          if (dataflowResult == null) return null;
          Set<Object> values = dataflowResult.getExpressionValues(expression);
          if (values.size() != 1) return null;
          if (!(values.iterator().next() instanceof String value)) {
            return null;
          }
          pattern = value;
        }

        MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat(pattern);
        if (result.valid()) {
          return result.placeholders();
        }

        if (!immediatePattern) {
          Optional<MessageFormatUtil.MessageFormatError> toHighlight =
            result.errors().stream()
              .filter(t-> t.errorType().getSeverity().ordinal() <= MessageFormatUtil.ErrorSeverity.WARNING.ordinal())
              .min(Comparator.comparing(t -> t.errorType().getSeverity()));
          if (toHighlight.isEmpty()) {
            return null;
          }
          MessageFormatUtil.MessageFormatError error = toHighlight.get();
          createError(expression, error.errorType(), getRelatedText(pattern, error), 0, expression.getTextLength(),
                      InspectionGadgetsBundle.message("inspection.incorrect.message.format.pattern", pattern));
        }
        else {
          for (MessageFormatUtil.MessageFormatError error : result.errors()) {
            createError(expression, error.errorType(), getRelatedText(pattern, error), error.fromIndex(), error.toIndex(), null);
          }
        }

        if (!ContainerUtil.exists(result.errors(),
                                  error -> error.errorType().getSeverity() == MessageFormatUtil.ErrorSeverity.RUNTIME_EXCEPTION)) {
          return result.placeholders();
        }
        return null;
      }

      @Nullable
      private static String getRelatedText(@NotNull String pattern, @NotNull MessageFormatUtil.MessageFormatError error) {
        if (error.fromIndex() < 0 || error.toIndex() > pattern.length() || error.toIndex() < error.fromIndex()) {
          return null;
        }
        return pattern.substring(error.fromIndex(), error.toIndex());
      }

      private void createError(@NotNull PsiExpression expression,
                               @NotNull MessageFormatUtil.MessageFormatErrorType type,
                               @Nullable String relatedText,
                               int start, int from, @Nullable @Nls String message) {
        //it's relevant mostly for IDEA files
        if (type == MessageFormatUtil.MessageFormatErrorType.QUOTED_PLACEHOLDER) {
          return;
        }
        if (relatedText == null) {
          return;
        }
        String errorText = getMessageFormatTemplate(type, relatedText);
        if (message != null) {
          errorText = message + " " + errorText;
        }
        ProblemHighlightType highlightType = getCustomHighlightType(type);
        if (highlightType == null) {
          holder.registerProblem(expression, TextRange.create(start, from), errorText);
        }
        else {
          holder.registerProblem(expression, errorText, highlightType, TextRange.create(start, from));
        }
      }

      @Nullable
      private static ProblemHighlightType getCustomHighlightType(@NotNull MessageFormatUtil.MessageFormatErrorType type) {
        if (type.getSeverity() == MessageFormatUtil.ErrorSeverity.WARNING ||
            type.getSeverity() == MessageFormatUtil.ErrorSeverity.WEAK_WARNING) {
          return ProblemHighlightType.WEAK_WARNING;
        }
        return null;
      }
    };
  }

  @NotNull
  public static @Nls String getMessageFormatTemplate(@NotNull MessageFormatUtil.MessageFormatErrorType type, @NotNull String relatedText) {
    return switch (type) {
      case QUOTED_PLACEHOLDER ->
        InspectionGadgetsBundle.message("inspection.incorrect.message.format.quotes.around.parameter", relatedText);
      case UNPARSED_INDEX, INDEX_NEGATIVE ->
        InspectionGadgetsBundle.message("inspection.incorrect.message.format.incorrect.index", relatedText);
      case UNKNOWN_FORMAT_TYPE -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.unknown.format.type", relatedText);
      case UNCLOSED_BRACE_PLACEHOLDER -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.unclosed.brace");
      case UNPAIRED_QUOTE -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.unpaired.quote");
      case UNMATCHED_BRACE -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.unmatched.brace");
      case MANY_QUOTES -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.incorrect.quotes.number");
      case INCORRECT_CHOICE_SELECTOR ->
        InspectionGadgetsBundle.message("inspection.incorrect.message.format.choice.limit.incorrect", relatedText);
      case SELECTOR_NOT_FOUND -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.choice.limit.not.found");
      case INCORRECT_ORDER_CHOICE_SELECTOR -> InspectionGadgetsBundle.message("inspection.incorrect.message.format.incorrect.order.choice");
    };
  }
}
