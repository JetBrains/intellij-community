// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.siyeh.InspectionGadgetsBundle.BUNDLE;

public class RedundantStringOperationInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher STRING_TO_STRING = CallMatcher.instanceCall(JAVA_LANG_STRING, "toString").parameterCount(0);
  private static final CallMatcher STRING_INTERN = CallMatcher.instanceCall(JAVA_LANG_STRING, "intern").parameterCount(0);
  private static final CallMatcher STRING_LENGTH = CallMatcher.instanceCall(JAVA_LANG_STRING, "length").parameterCount(0);
  private static final CallMatcher STRING_SUBSTRING = CallMatcher.anyOf(
      CallMatcher.instanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int"),
      CallMatcher.instanceCall(JAVA_LANG_STRING, "substring").parameterTypes("int", "int"));
  private static final CallMatcher STRING_BUILDER_APPEND =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "append")
      .parameterTypes(JAVA_LANG_STRING);
  private static final CallMatcher PRINTSTREAM_PRINTLN = CallMatcher.instanceCall("java.io.PrintStream", "println")
    .parameterTypes(JAVA_LANG_STRING);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
          if (STRING_TO_STRING.test(call)) {
            registerProblem(call, "inspection.redundant.string.call.message");
          }
          else if (STRING_SUBSTRING.test(call)) {
            processSubstring(call);
          }
          else if (STRING_BUILDER_APPEND.test(call)) {
            if (getSingleEmptyStringArgument(call) != null) {
              registerProblem(call, "inspection.redundant.string.call.message");
            }
          }
          else if (STRING_INTERN.test(call) && PsiUtil.isConstantExpression(qualifier)) {
            registerProblem(call, "inspection.redundant.string.intern.on.constant.message");
          }
          else if (PRINTSTREAM_PRINTLN.test(call)) {
            checkUnnecessaryEmptyStringArgument(call);
          }
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiJavaCodeReferenceElement classRef = expression.getClassReference();
        if (classRef == null) return;
        String className = classRef.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className) || CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className)) {
          checkUnnecessaryEmptyStringArgument(expression);
        }
      }

      private void checkUnnecessaryEmptyStringArgument(PsiCall call) {
        PsiExpression argument = getSingleEmptyStringArgument(call);
        if (argument != null) {
          LocalQuickFix fix =
            new DeleteElementFix(argument, InspectionGadgetsBundle.message("inspection.redundant.string.remove.argument.fix.name"));
          holder.registerProblem(argument, InspectionGadgetsBundle.message("inspection.redundant.string.argument.message"),
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
        }
      }

      private PsiExpression getSingleEmptyStringArgument(PsiCall call) {
        PsiExpressionList argList = call.getArgumentList();
        if (argList == null) return null;
        PsiExpression[] args = argList.getExpressions();
        if (args.length != 1) return null;
        return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(args[0]), "") ? args[0] : null;
      }

      private void processSubstring(PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (!ExpressionUtils.isZero(args[0])) return;
        if (args.length == 2) {
          PsiMethodCallExpression argCall =
            ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(args[1]), PsiMethodCallExpression.class);
          if (!STRING_LENGTH.test(argCall) ||
              !EquivalenceChecker.getCanonicalPsiEquivalence()
                .expressionsAreEquivalent(call.getMethodExpression().getQualifierExpression(),
                                          argCall.getMethodExpression().getQualifierExpression())) {
            return;
          }
        }
        registerProblem(call, "inspection.redundant.string.call.message");
      }

      private void registerProblem(PsiMethodCallExpression call, @NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
        String name = call.getMethodExpression().getReferenceName();
        holder.registerProblem(call, InspectionGadgetsBundle.message(key),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, getRange(call), new ReplaceWithQualifierFix(name));
      }
    };
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

  private static class ReplaceWithQualifierFix implements LocalQuickFix {
    private String myToRemove;

    public ReplaceWithQualifierFix(String toRemove) {
      myToRemove = toRemove;
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
      PsiExpression result = (PsiExpression)ct.replaceAndRestoreComments(call, ct.markUnchanged(qualifier));
      if (result.getParent() instanceof PsiExpressionStatement) {
        extractSideEffects(result, (PsiExpressionStatement)result.getParent());
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
