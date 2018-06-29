// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.InspectionGadgetsBundle.BUNDLE;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class RedundantStringOperationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  enum FixType {
    REPLACE_WITH_QUALIFIER,
    REPLACE_WITH_ARGUMENTS
  }

  private static final CallMatcher STRING_TO_STRING = instanceCall(JAVA_LANG_STRING, "toString").parameterCount(0);
  private static final CallMatcher STRING_INTERN = instanceCall(JAVA_LANG_STRING, "intern").parameterCount(0);
  private static final CallMatcher STRING_LENGTH = instanceCall(JAVA_LANG_STRING, "length").parameterCount(0);
  private static final CallMatcher STRING_SUBSTRING = anyOf(
    instanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int"),
    instanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int", "int"));
  private static final CallMatcher STRING_BUILDER_APPEND =
    instanceCall(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "append").parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher PRINTSTREAM_PRINTLN = instanceCall("java.io.PrintStream", "println")
    .parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER =
    instanceCall(JAVA_LANG_STRING, "indexOf", "startsWith").parameterCount(2);
  private static final CallMatcher STRING_LAST_INDEX_OF = instanceCall(JAVA_LANG_STRING, "lastIndexOf").parameterCount(2);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantStringOperationVisitor(holder, isOnTheFly);
  }

  private static class RedundantStringOperationVisitor extends JavaElementVisitor {
    private final CallMapper<ProblemDescriptor> myProcessors = new CallMapper<ProblemDescriptor>()
      .register(STRING_TO_STRING, call -> getProblem(call, "inspection.redundant.string.call.message"))
      .register(STRING_SUBSTRING, this::getSubstringProblem)
      .register(STRING_BUILDER_APPEND, this::getAppendProblem)
      .register(STRING_INTERN, this::getInternProblem)
      .register(PRINTSTREAM_PRINTLN, call -> getRedundantArgumentProblem(getSingleEmptyStringArgument(call)))
      .register(METHOD_WITH_REDUNDANT_ZERO_AS_SECOND_PARAMETER, this::getRedundantZeroAsSecondParameterProblem)
      .register(STRING_LAST_INDEX_OF, this::getLastIndexOfProblem);
    private final InspectionManager myManager;
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    public RedundantStringOperationVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
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
      if (ConstructionUtils.isReferenceTo(classRef, CommonClassNames.JAVA_LANG_STRING_BUILDER, CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
        ProblemDescriptor descriptor = getRedundantArgumentProblem(getSingleEmptyStringArgument(expression));
        if (descriptor == null) return;
        myHolder.registerProblem(descriptor);
      }
    }

    @Nullable
    private ProblemDescriptor getAppendProblem(PsiMethodCallExpression call) {
      return getSingleEmptyStringArgument(call) != null ? getProblem(call, "inspection.redundant.string.call.message") : null;
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
        if (binOp.getOperationTokenType().equals(JavaTokenType.MINUS) &&
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
  }

  private static class RemoveRedundantStringCallFix implements LocalQuickFix {
    private final FixType myFixType;
    private final String myToRemove;

    public RemoveRedundantStringCallFix(String toRemove, FixType fixType) {
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
      PsiExpression qualifier = ExpressionUtils.getQualifierOrThis(call.getMethodExpression());
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
      if (Collections.singletonList(result).equals(sideEffects)) return;

      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, result);
      if (statements.length > 0) {
        PsiStatement lastAdded = BlockUtils.addBefore(statement, statements);
        statement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiStatement.class));
      }
      statement.delete();
    }
  }
}
