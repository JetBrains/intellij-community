// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.HardcodedMethodConstants.EQUALS_IGNORE_CASE;
import static com.siyeh.InspectionGadgetsBundle.BUNDLE;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class RedundantStringOperationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  enum FixType {
    REPLACE_WITH_QUALIFIER,
    REPLACE_WITH_ARGUMENTS
  }

  private static final CallMatcher STRING_TO_STRING = exactInstanceCall(JAVA_LANG_STRING, "toString").parameterCount(0);
  private static final CallMatcher STRING_INTERN = exactInstanceCall(JAVA_LANG_STRING, "intern").parameterCount(0);
  private static final CallMatcher STRING_LENGTH = exactInstanceCall(JAVA_LANG_STRING, "length").parameterCount(0);
  private static final CallMatcher STRING_SUBSTRING_ONE_ARG = exactInstanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int");
  private static final CallMatcher STRING_SUBSTRING_TWO_ARG = exactInstanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int", "int");
  private static final CallMatcher STRING_SUBSTRING = anyOf(STRING_SUBSTRING_ONE_ARG, STRING_SUBSTRING_TWO_ARG);
  private static final CallMatcher STRING_BUILDER_APPEND =
    instanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "append").parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher STRING_BUILDER_TO_STRING = instanceCall(JAVA_LANG_STRING_BUILDER, "toString").parameterCount(0);
  private static final CallMatcher PRINTSTREAM_PRINTLN = instanceCall("java.io.PrintStream", "println")
    .parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER =
    exactInstanceCall(JAVA_LANG_STRING, "indexOf", "startsWith").parameterCount(2);
  private static final CallMatcher STRING_LAST_INDEX_OF = exactInstanceCall(JAVA_LANG_STRING, "lastIndexOf").parameterCount(2);
  private static final CallMatcher STRING_IS_EMPTY = exactInstanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0);
  private static final CallMatcher CASE_CHANGE = exactInstanceCall(JAVA_LANG_STRING, "toUpperCase", "toLowerCase");
  private static final CallMatcher STRING_EQUALS = exactInstanceCall(JAVA_LANG_STRING, "equals").parameterTypes(JAVA_LANG_OBJECT);
  private static final CallMatcher STRING_EQUALS_IGNORE_CASE =
    exactInstanceCall(JAVA_LANG_STRING, "equalsIgnoreCase").parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher CHANGE_CASE = anyOf(exactInstanceCall(JAVA_LANG_STRING, "toLowerCase").parameterCount(0),
                                                       exactInstanceCall(JAVA_LANG_STRING, "toUpperCase").parameterCount(0));

  public boolean ignoreStringConstructor = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), this,
      "ignoreStringConstructor");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "StringOperationCanBeSimplified";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantStringOperationVisitor(holder, isOnTheFly, this);
  }

  private static class RedundantStringOperationVisitor extends JavaElementVisitor {
    private final CallMapper<ProblemDescriptor> myProcessors = new CallMapper<ProblemDescriptor>()
      .register(STRING_TO_STRING, call -> getProblem(call, "inspection.redundant.string.call.message"))
      .register(STRING_SUBSTRING, this::getSubstringProblem)
      .register(STRING_BUILDER_APPEND, this::getAppendProblem)
      .register(STRING_BUILDER_TO_STRING, this::getRedundantStringBuilderToStringProblem)
      .register(STRING_INTERN, this::getInternProblem)
      .register(PRINTSTREAM_PRINTLN, call -> getRedundantArgumentProblem(getSingleEmptyStringArgument(call)))
      .register(METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER, this::getRedundantZeroAsSecondParameterProblem)
      .register(STRING_LAST_INDEX_OF, this::getLastIndexOfProblem)
      .register(STRING_IS_EMPTY, this::getRedundantCaseChangeProblem)
      .register(STRING_EQUALS, this::getRedundantSubstringEqualsProblem)
      .register(anyOf(STRING_EQUALS, STRING_EQUALS_IGNORE_CASE), this::getRedundantCaseEqualsProblem);
    private final InspectionManager myManager;
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;
    private final RedundantStringOperationInspection myInspection;

    RedundantStringOperationVisitor(ProblemsHolder holder, boolean isOnTheFly, RedundantStringOperationInspection inspection) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
      myInspection = inspection;
      myManager = myHolder.getManager();
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      myProcessors.mapAll(call).forEach(myHolder::registerProblem);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      PsiJavaCodeReferenceElement classRef = expression.getClassReference();
      ProblemDescriptor descriptor = null;
      if (ConstructionUtils.isReferenceTo(classRef, JAVA_LANG_STRING_BUILDER, JAVA_LANG_STRING_BUFFER)) {
        descriptor = getRedundantArgumentProblem(getSingleEmptyStringArgument(expression));
      }
      else if (ConstructionUtils.isReferenceTo(classRef, JAVA_LANG_STRING) && !myInspection.ignoreStringConstructor) {
        descriptor = getStringConstructorProblem(expression);
      }
      if (descriptor != null) {
        myHolder.registerProblem(descriptor);
      }
    }

    private ProblemDescriptor getStringConstructorProblem(PsiNewExpression expression) {
      PsiExpressionList args = expression.getArgumentList();
      if (args == null) return null;
      if (args.isEmpty()) {
        LocalQuickFix[] fixes = {
          new StringConstructorFix(true),
          new SetInspectionOptionFix(
            myInspection, "ignoreStringConstructor",
            InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), true)};
        return myManager.createProblemDescriptor(expression, (TextRange)null, 
                                                 InspectionGadgetsBundle.message("inspection.redundant.string.constructor.message"),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly, fixes);
      }
      if (args.getExpressionCount() == 1) {
        PsiExpression arg = args.getExpressions()[0];
        if (TypeUtils.isJavaLangString(arg.getType()) &&
            (PsiUtil.isLanguageLevel7OrHigher(expression) || !STRING_SUBSTRING.matches(arg))) {
          TextRange range = new TextRange(0, args.getStartOffsetInParent());
          LocalQuickFix[] fixes = {
            new StringConstructorFix(false),
            new SetInspectionOptionFix(
              myInspection, "ignoreStringConstructor",
              InspectionGadgetsBundle.message("inspection.redundant.string.option.do.not.report.string.constructors"), true)};
          return myManager.createProblemDescriptor(expression, range,
                                                   InspectionGadgetsBundle.message("inspection.redundant.string.constructor.message"),
                                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly, fixes);
        }
      }
      return null;
    }

    private ProblemDescriptor getRedundantCaseEqualsProblem(PsiMethodCallExpression call) {

      PsiExpression equalTo = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);

      //case: "foo".equals(s.toLowerCase())
      if (equalTo instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression equalsToCallExpression = (PsiMethodCallExpression)equalTo;
        if (isChangeCaseCall(equalsToCallExpression) &&
            PsiUtil.isConstantExpression(call.getMethodExpression().getQualifierExpression())) {
          PsiElement anchor = equalsToCallExpression.getMethodExpression().getReferenceNameElement();
          if (anchor == null) {
            return null;
          }
          return createProblem(equalsToCallExpression, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.RIGHT);
        }
      }

      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return null;

      //cases:
      //- text1.toLowerCase().equals("test2")
      //- text1.toLowerCase().equals(text2.toLowerCase())
      if (isChangeCaseCall(qualifierCall)) {
        PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
        if (anchor == null) {
          return null;
        }
        //case: text1.toLowerCase().equals("test2")
        if (PsiUtil.isConstantExpression(equalTo)) {
          return createProblem(qualifierCall, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.LEFT);
        }

        //case: text1.toLowerCase().equals(text2.toLowerCase())
        if (equalTo instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression secondCall = (PsiMethodCallExpression)equalTo;
          if (isEqualChangeCaseCall(qualifierCall, secondCall)) {
            return createProblem(secondCall, anchor, RemoveRedundantChangeCaseFix.PlaceCaseEqualType.BOTH);
          }
        }
      }

      return null;
    }

    @Nullable
    private ProblemDescriptor createProblem(PsiMethodCallExpression equalsToCallExpression,
                                            PsiElement anchor,
                                            RemoveRedundantChangeCaseFix.PlaceCaseEqualType type) {
      String nameMethod = equalsToCallExpression.getMethodExpression().getReferenceName();
      if (nameMethod == null) {
        return null;
      }
      return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                               InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly,
                                               new RemoveRedundantChangeCaseFix(nameMethod, type));
    }


    private static boolean isChangeCaseCall(@NotNull PsiMethodCallExpression qualifierCall) {
      return CHANGE_CASE.test(qualifierCall);
    }

    private static boolean isEqualChangeCaseCall(@NotNull PsiMethodCallExpression qualifierCall,
                                                 @NotNull PsiMethodCallExpression secondCall) {
      return CHANGE_CASE.test(qualifierCall) &&
             CHANGE_CASE.test(secondCall) &&
             qualifierCall.getMethodExpression().getReferenceName() != null &&
             qualifierCall.getMethodExpression().getReferenceName().equals(secondCall.getMethodExpression().getReferenceName());
    }

    private ProblemDescriptor getRedundantSubstringEqualsProblem(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
      if (receiver == null) return null;
      if (STRING_SUBSTRING_TWO_ARG.test(qualifierCall)) {
        PsiExpression equalTo = call.getArgumentList().getExpressions()[0];
        PsiExpression[] args = qualifierCall.getArgumentList().getExpressions();
        boolean lengthMatches = lengthMatches(equalTo, args[0], args[1]);
        if (lengthMatches) {
          PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
          if (anchor != null) {
            if (equalTo instanceof PsiLiteralExpression) {
              final Object equalsValue = ((PsiLiteralExpression)equalTo).getValue();
              if (equalsValue instanceof String && StringUtil.length((String)equalsValue) == 1) {
                return createSubstringToCharAtProblemDescriptor(call);
              }
            }
            return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                     InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly,
                                                     new RemoveRedundantSubstringFix("startsWith"));
          }
        }
      }
      if (STRING_SUBSTRING_ONE_ARG.test(qualifierCall)) {
        PsiExpression equalTo = call.getArgumentList().getExpressions()[0];
        PsiExpression from = qualifierCall.getArgumentList().getExpressions()[0];
        PsiExpression to = getLengthExpression(receiver, JavaPsiFacade.getElementFactory(myHolder.getProject()));
        boolean lengthMatches = lengthMatches(equalTo, from, to);
        if (lengthMatches) {
          PsiElement anchor = qualifierCall.getMethodExpression().getReferenceNameElement();
          if (anchor != null) {
            return myManager.createProblemDescriptor(anchor, (TextRange)null,
                                                     InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly,
                                                     new RemoveRedundantSubstringFix("endsWith"));
          }
        }
      }
      return null;
    }

    /**
     * This method is meant to generate a {@link ProblemDescriptor} for a call like
     * <pre>
     * stringValue.substring(i, i + 1).equals("_")
     * </pre>
     * which can be replaced with a more readable version
     * <pre>
     *   stringValue.charAt(i) == '_'
     * </pre>
     * <hr />
     * <p>
     * <strong>IMPORTANT:</strong> this method is meant to be called only from
     * {@link RedundantStringOperationVisitor#getRedundantSubstringEqualsProblem(PsiMethodCallExpression)}
     * </p>
     *
     * @param call the original expression to generate a {@link ProblemDescriptor} for
     * @return generated instance of {@link ProblemDescriptor}
     */
    @NotNull
    private ProblemDescriptor createSubstringToCharAtProblemDescriptor(@NotNull final PsiMethodCallExpression call) {
      final String converted = SubstringEqualsToCharAtEqualsQuickFix.getTargetString(call, PsiElement::getText);
      assert converted != null : "Message cannot be null";

      final PsiElement outermostEqualsExpr = getOutermostEquals(call);
      final SubstringEqualsToCharAtEqualsQuickFix fix = new SubstringEqualsToCharAtEqualsQuickFix(outermostEqualsExpr.getText(),
                                                                                                  converted);
      return myManager.createProblemDescriptor(outermostEqualsExpr,
                                               InspectionGadgetsBundle.message("inspection.x.call.can.be.replaced.with.y", "substring()", "charAt()"),
                                               fix,
                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly);
    }

    private static boolean isNegated(@NotNull final PsiExpression start, boolean negated) {
      final PsiExpression negation = BoolUtils.findNegation(start);
      if (negation == null) return negated;
      else return isNegated(negation, !negated);
    }

    private static PsiExpression getOutermostEquals(@NotNull final PsiExpression start) {
      final PsiExpression negation = BoolUtils.findNegation(start);
      if (negation == null) return start;
      else return getOutermostEquals(negation);
    }

    private boolean lengthMatches(PsiExpression equalTo, PsiExpression from, PsiExpression to) {
      String str = tryCast(ExpressionUtils.computeConstantExpression(equalTo), String.class);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myHolder.getProject());
      if (str != null) {
        PsiExpression lengthExpression =
          factory.createExpressionFromText(String.valueOf(str.length()), equalTo);
        if (ExpressionUtils.isDifference(from, to, lengthExpression)) return true;
      }
      PsiExpression lengthExpression = getLengthExpression(equalTo, factory);
      return ExpressionUtils.isDifference(from, to, lengthExpression);
    }

    private static PsiExpression getLengthExpression(PsiExpression string, PsiElementFactory factory) {
      return factory
            .createExpressionFromText(ParenthesesUtils.getText(string, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".length()", string);
    }

    @Nullable
    private ProblemDescriptor getRedundantCaseChangeProblem(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (CASE_CHANGE.test(qualifierCall)) {
        return getProblem(qualifierCall, "inspection.redundant.string.call.message");
      }
      return null;
    }

    @Nullable
    private ProblemDescriptor getAppendProblem(PsiMethodCallExpression call) {
      return getSingleEmptyStringArgument(call) != null ? getProblem(call, "inspection.redundant.string.call.message") : null;
    }

    @Nullable
    private ProblemDescriptor getRedundantStringBuilderToStringProblem(@NotNull final PsiMethodCallExpression call) {
      final PsiMethodCallExpression substringCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
      if (substringCall == null) return null;

      final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(substringCall.getMethodExpression().getQualifierExpression());
      if (qualifier != call || !STRING_SUBSTRING.test(substringCall)) return null;

      return getProblem(call, "inspection.redundant.string.call.message");
    }

    @Nullable
    private ProblemDescriptor getInternProblem(PsiMethodCallExpression call) {
      return PsiUtil.isConstantExpression(call.getMethodExpression().getQualifierExpression())
             ? getProblem(call, "inspection.redundant.string.intern.on.constant.message")
             : null;
    }

    @Nullable
    private ProblemDescriptor getLastIndexOfProblem(PsiMethodCallExpression call) {
      PsiExpression secondArg = call.getArgumentList().getExpressions()[1];
      PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(secondArg);
      // s.lastIndexOf(..., s.length()) or s.lastIndexOf(..., s.length() - 1)
      if (stripped instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)stripped;
        if (binOp.getOperationTokenType() == JavaTokenType.MINUS &&
            ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(binOp.getROperand()), 1)) {
          stripped = binOp.getLOperand();
        }
      }
      return isLengthOf(stripped, call.getMethodExpression().getQualifierExpression()) ? getRedundantArgumentProblem(secondArg) : null;
    }

    @Nullable
    private ProblemDescriptor getRedundantZeroAsSecondParameterProblem(PsiMethodCallExpression call) {
      PsiExpression secondArg = call.getArgumentList().getExpressions()[1];
      if (ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(secondArg), 0)) {
        return getRedundantArgumentProblem(secondArg);
      }
      return null;
    }

    @Nullable
    private ProblemDescriptor getRedundantArgumentProblem(@Nullable PsiExpression argument) {
      if (argument == null) return null;
      LocalQuickFix fix =
        new DeleteElementFix(argument, InspectionGadgetsBundle.message("inspection.redundant.string.remove.argument.fix.name"));
      return myManager.createProblemDescriptor(argument,
                                               InspectionGadgetsBundle.message(
                                                 "inspection.redundant.string.argument.message"),
                                               myIsOnTheFly,
                                               new LocalQuickFix[]{fix},
                                               ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }

    @Nullable
    private static PsiExpression getSingleEmptyStringArgument(PsiCall call) {
      PsiExpressionList argList = call.getArgumentList();
      if (argList == null) return null;
      PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) return null;
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(args[0]), "") ? args[0] : null;
    }

    @Nullable
    private ProblemDescriptor getSubstringProblem(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression stringExpression = call.getMethodExpression().getQualifierExpression();
      if (args.length == 1) {
        return ExpressionUtils.isZero(args[0]) ? getProblem(call, "inspection.redundant.string.call.message") : null;
      }
      // args.length == 2
      if (isLengthOf(args[1], stringExpression)) {
        if (ExpressionUtils.isZero(args[0])) {
          return getProblem(call, "inspection.redundant.string.call.message");
        }
        DeleteElementFix fix =
          new DeleteElementFix(args[1], InspectionGadgetsBundle.message("inspection.redundant.string.remove.argument.fix.name"));
        return myManager.createProblemDescriptor(args[1],
                                                 InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                 fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly);
      }

      boolean betterWithCharAt = isBetterWithCharAt(call);
      if (betterWithCharAt) {
        final PsiElement substring = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());

        final String converted = String.format("%s.charAt(%s)",
                                               Objects.requireNonNull(stringExpression).getText(),
                                               args[0].getText());

        final SubstringToCharAtQuickFix fix = new SubstringToCharAtQuickFix(call.getText(), converted);

        final TextRange textRange = new TextRange(substring.getStartOffsetInParent(),
                                                  substring.getStartOffsetInParent() + substring.getTextLength());
        return myManager.createProblemDescriptor(call, textRange,
                                                 CommonQuickFixBundle.message("fix.replace.x.with.y", call.getText(), converted),
                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myIsOnTheFly,
                                                 fix);
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      if (parent instanceof PsiExpressionList && ((PsiExpressionList)parent).getExpressionCount() == 1) {
        PsiMethodCallExpression parentCall = tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (STRING_BUILDER_APPEND.test(parentCall)) {
          PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
          return myManager.createProblemDescriptor(nameElement,
                                                   InspectionGadgetsBundle.message("inspection.redundant.string.call.message"),
                                                   new RemoveRedundantStringCallFix(
                                                     nameElement.getText(), FixType.REPLACE_WITH_ARGUMENTS),
                                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly);
        }
      }
      return null;
    }

    /**
     * This method examines the passed {@link PsiMethodCallExpression} that contains {@link String#substring(int, int)}
     * to decide if it should be altered with {@link String#charAt(int)}<br/>
     * <pre>
     * args[1] - args[0] == 1
     *    yes          no
     *    |             |
     *    |             |- return false
     *    |
     *    |-ExpressionUtils.isConversionToStringNecessary(call, false) == true
     *                    yes                  no
     *                     |                   |- return true
     *                     |- return false
     * </pre>
     * The method checks if the difference between the arguments of {@link String#substring(int, int)} is equal to "1"
     * and if so it checks if the expression should be converted to string according to its surroundings.
     * If the expression is not required to be converted to string explicitly then the expression is a good candidate
     * to be converted with {@link String#charAt(int)}
     *
     * @param call an expression to examine
     * @return true if the expression is a good candidate to to be converted with {@link String#charAt(int)}, otherwise - false
     */
    private static boolean isBetterWithCharAt(@NotNull final PsiMethodCallExpression call) {
      final PsiExpression[] args = call.getArgumentList().getExpressions();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());

      final PsiExpression one = factory.createExpressionFromText("1", null);
      final boolean diffByOne = ExpressionUtils.isDifference(args[0], args[1], one);
      if (!diffByOne) return false;

      return !ExpressionUtils.isConversionToStringNecessary(call, false);
    }

    private static boolean isLengthOf(PsiExpression stringLengthCandidate, PsiExpression stringExpression) {
      PsiMethodCallExpression argCall = tryCast(PsiUtil.skipParenthesizedExprDown(stringLengthCandidate), PsiMethodCallExpression.class);
      return STRING_LENGTH.test(argCall) &&
             EquivalenceChecker.getCanonicalPsiEquivalence()
               .expressionsAreEquivalent(stringExpression, argCall.getMethodExpression().getQualifierExpression());
    }

    @NotNull
    private ProblemDescriptor getProblem(PsiMethodCallExpression call, @NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
      String name = call.getMethodExpression().getReferenceName();
      return myManager.createProblemDescriptor(call, getRange(call), InspectionGadgetsBundle.message(key),
                                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, myIsOnTheFly,
                                               new RemoveRedundantStringCallFix(name, FixType.REPLACE_WITH_QUALIFIER));
    }

    @NotNull
    private static TextRange getRange(PsiMethodCallExpression call) {
      PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
      if (nameElement != null) {
        TextRange callRange = call.getTextRange();
        return new TextRange(nameElement.getTextRange().getStartOffset(), callRange.getEndOffset()).shiftLeft(
          callRange.getStartOffset());
      }
      return call.getTextRange();
    }

    /**
     * An instance of {@link LocalQuickFix} for problems that can be solved by replacing
     * {@link String#substring(int, int)} with {@link String#charAt(int)}
     */
    private static class SubstringToCharAtQuickFix implements LocalQuickFix {
      @NotNull private final String myText;
      @NotNull private final String myConverted;

      SubstringToCharAtQuickFix(@NotNull final String text,
                                @NotNull final String converted) {
        myText = text;
        myConverted = converted;
      }

      @Override
      public @IntentionName @NotNull String getName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", myText, myConverted);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", "substring()", "charAt()");
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        PsiMethodCallExpression call = tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
        if (call == null) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 2) return;
        ExpressionUtils.bindCallTo(call, "charAt");
        new CommentTracker().deleteAndRestoreComments(args[1]);
      }
    }

    /**
     * An instance of {@link LocalQuickFix} for problems like
     * <pre>
     *   stringValue.substring(i, i + 1).equals("_")
     * </pre>
     * that can be changed to
     * <pre>
     *   stringValue.charAt(i) == '_'
     * </pre>
     */
    private static class SubstringEqualsToCharAtEqualsQuickFix implements LocalQuickFix {
      @NotNull private final String myText;
      @NotNull private final String myConverted;

      SubstringEqualsToCharAtEqualsQuickFix(@NotNull final String text,
                                            @NotNull final String converted) {
        myText = text;
        myConverted = converted;
      }

      @Override
      public @IntentionName @NotNull String getName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", myText, myConverted);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.replace.x.with.y", "substring()", "charAt()");
      }

      @Override
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element == null) return;

        final PsiMethodCallExpression call;

        if (element instanceof PsiMethodCallExpression) {
          call = (PsiMethodCallExpression)element;
        }
        else {
          // Strip PsiPrefixExpression
          call = PsiTreeUtil.findChildOfType(element, PsiMethodCallExpression.class);
        }

        if (call == null) return;

        final CommentTracker ct = new CommentTracker();
        final String convertTo = getTargetString(call, ct::text);
        if (convertTo == null) return;

        ct.replaceAndRestoreComments(element, convertTo);
      }

      @Nullable
      private static String getTargetString(@NotNull final PsiMethodCallExpression call,
                                            @NotNull Function<@NotNull PsiElement, @NotNull String> textExtractor) {
        final PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (qualifierCall == null) return null;

        final PsiExpression receiver = qualifierCall.getMethodExpression().getQualifierExpression();
        if (receiver == null) return null;

        final PsiExpression[] args = qualifierCall.getArgumentList().getExpressions();
        if (args.length != 2) return null;

        final PsiExpression equalTo = call.getArgumentList().getExpressions()[0];

        final String eqSign = isNegated(call, false) ? "!=" : "==";

        final String equalToValue = PsiLiteralUtil.charLiteralForCharString(textExtractor.apply(equalTo));

        return String.format("%s.charAt(%s) %s %s",
                             textExtractor.apply(receiver),
                             textExtractor.apply(args[0]),
                             eqSign,
                             equalToValue
        );
      }
    }
  }


  private static class RemoveRedundantChangeCaseFix implements LocalQuickFix {
    private final @NotNull String caseRedundant;
    private final @NotNull PlaceCaseEqualType myPlaceCaseEqualType;

    private enum PlaceCaseEqualType {
      LEFT, RIGHT, BOTH
    }

    RemoveRedundantChangeCaseFix(@NotNull String caseRedundant, @NotNull PlaceCaseEqualType placeCaseEqualType) {
      this.caseRedundant = caseRedundant;
      this.myPlaceCaseEqualType = placeCaseEqualType;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", EQUALS_IGNORE_CASE, caseRedundant);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

      PsiMethodCallExpression changeCaseCall = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (changeCaseCall == null) return;

      if (myPlaceCaseEqualType == PlaceCaseEqualType.RIGHT) {
        fixRightChangeCase(changeCaseCall);
        return;
      }

      fixLeftAndBothChangeCase(changeCaseCall);
    }

    private static void fixRightChangeCase(PsiMethodCallExpression changeCaseCall) {
      PsiMethodCallExpression equalsCall = PsiTreeUtil.getParentOfType(changeCaseCall, PsiMethodCallExpression.class);
      if (equalsCall == null) return;
      PsiExpression qualifierBeforeChangeCase = ExpressionUtils.getEffectiveQualifier(changeCaseCall.getMethodExpression());
      if (qualifierBeforeChangeCase == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(changeCaseCall, qualifierBeforeChangeCase);
      ExpressionUtils.bindCallTo(equalsCall, EQUALS_IGNORE_CASE);
    }

    private void fixLeftAndBothChangeCase(PsiMethodCallExpression changeCaseCall) {
      PsiExpression qualifierBeforeChangeCase = changeCaseCall.getMethodExpression().getQualifierExpression();
      if (qualifierBeforeChangeCase == null) return;
      PsiMethodCallExpression equalsCall = ExpressionUtils.getCallForQualifier(changeCaseCall);
      if (equalsCall == null) return;
      if (myPlaceCaseEqualType == PlaceCaseEqualType.BOTH) {
        PsiExpression secondChangeCaseCall = PsiUtil.skipParenthesizedExprDown(equalsCall.getArgumentList().getExpressions()[0]);
        if (secondChangeCaseCall == null) return;
        PsiExpression secondQualifierBeforeChangeCase =
          ExpressionUtils.getEffectiveQualifier(((PsiMethodCallExpression)secondChangeCaseCall).getMethodExpression());
        if (secondQualifierBeforeChangeCase == null) return;
        CommentTracker ct = new CommentTracker();
        ct.replaceAndRestoreComments(secondChangeCaseCall, secondQualifierBeforeChangeCase);
      }
      ExpressionUtils.bindCallTo(equalsCall, EQUALS_IGNORE_CASE);
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(changeCaseCall, qualifierBeforeChangeCase);
    }
  }

  private static class RemoveRedundantSubstringFix implements LocalQuickFix {
    private final @NotNull String myBindCallName;
    
    RemoveRedundantSubstringFix(@NotNull String bindCallName) {
      myBindCallName = bindCallName;
    }
    
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("remove.redundant.string.fix.text", myBindCallName, "substring");
    }
    
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.redundant.substring.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression substringCall = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (substringCall == null) return;
      PsiExpression stringExpr = substringCall.getMethodExpression().getQualifierExpression();
      if (stringExpr == null) return;
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(substringCall);
      if (nextCall == null) return;
      PsiExpression[] args = substringCall.getArgumentList().getExpressions();
      if (args.length == 0) return;
      CommentTracker ct = new CommentTracker();
      if (!"endsWith".equals(myBindCallName) && !ExpressionUtils.isZero(args[0])) {
        nextCall.getArgumentList().add(ct.markUnchanged(args[0]));
      }
      ExpressionUtils.bindCallTo(nextCall, myBindCallName);
      ct.replaceAndRestoreComments(substringCall, stringExpr);
    }
  }

  private static class RemoveRedundantStringCallFix implements LocalQuickFix {
    private final FixType myFixType;
    private final String myToRemove;

    RemoveRedundantStringCallFix(String toRemove, FixType fixType) {
      myToRemove = toRemove;
      myFixType = fixType;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.remove.fix.name", myToRemove);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return;
      CommentTracker ct = new CommentTracker();
      switch (myFixType) {
        case REPLACE_WITH_QUALIFIER: {
          PsiExpression result = (PsiExpression)ct.replaceAndRestoreComments(call, qualifier);
          if (result.getParent() instanceof PsiExpressionStatement) {
            extractSideEffects(result, (PsiExpressionStatement)result.getParent());
          }
          break;
        }
        case REPLACE_WITH_ARGUMENTS:
          PsiExpressionList list = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiExpressionList.class);
          if (list == null) return;
          for (PsiExpression arg : call.getArgumentList().getExpressions()) {
            list.add(ct.markUnchanged(arg));
          }
          ct.replaceAndRestoreComments(call, qualifier);
          break;
      }
    }

    private static void extractSideEffects(PsiExpression result, PsiStatement statement) {
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(result);
      if (Objects.equals(Collections.singletonList(result), sideEffects)) return;

      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, result);
      if (statements.length > 0) {
        PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
        statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
      }
      statement.delete();
    }
  }

  private static class StringConstructorFix extends InspectionGadgetsFix {
    private final String myName;

    private StringConstructorFix(boolean noArguments) {
      if (noArguments) {
        myName = InspectionGadgetsBundle.message(
          "inspection.redundant.string.replace.with.empty.fix.name");
      }
      else {
        myName = InspectionGadgetsBundle.message(
          "inspection.redundant.string.replace.with.arg.fix.name");
      }
    }

    @Override
    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiNewExpression expression = (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      CommentTracker commentTracker = new CommentTracker();
      final String argText = (args.length == 1) ? commentTracker.text(args[0]) : "\"\"";

      PsiReplacementUtil.replaceExpression(expression, argText, commentTracker);
    }
  }
}
