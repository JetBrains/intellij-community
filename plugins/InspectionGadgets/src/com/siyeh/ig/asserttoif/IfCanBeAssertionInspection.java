/*
 * Copyright 2010-2018 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.asserttoif;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class IfCanBeAssertionInspection extends BaseInspection {
  private static final String GUAVA_PRECONDITIONS = "com.google.common.base.Preconditions";
  private static final String GUAVA_CHECK_NON_NULL = "checkNotNull";

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("if.can.be.assertion.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfToAssertionVisitor();
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    boolean isObjectsRequireNonNullAvailable = (boolean)infos[0];
    boolean isIfStatement = (boolean)infos[1];
    List<InspectionGadgetsFix> fixes = new ArrayList<>(2);
    if (isObjectsRequireNonNullAvailable) {
      fixes.add(new ReplaceWithObjectsNonNullFix(isIfStatement));
    }
    if (isIfStatement) {
      fixes.add(new IfToAssertionFix());
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  static PsiNewExpression getThrownNewException(PsiElement element) {
    if (element instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)element).getCodeBlock().getStatements();
      if (statements.length == 1) {
        return getThrownNewException(statements[0]);
      }
    }
    else if (element instanceof PsiThrowStatement) {
      final PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      final PsiExpression exception = ParenthesesUtils.stripParentheses(throwStatement.getException());
      if (exception instanceof PsiNewExpression) {
        return (PsiNewExpression)exception;
      }
    }
    return null;
  }

  private static class IfToAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (statement.getCondition() != null &&
          statement.getElseBranch() == null &&
          getThrownNewException(statement.getThenBranch()) != null) {
        registerStatementError(statement, PsiUtil.isLanguageLevel7OrHigher(statement) && ComparisonUtils.isNullComparison(statement.getCondition()), true);
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (MethodCallUtils.isCallToMethod(expression,
                                         GUAVA_PRECONDITIONS,
                                         null,
                                         GUAVA_CHECK_NON_NULL,
                                         (PsiType[])null) && expression.getArgumentList().getExpressions().length <= 2) { // for parametrized messages we don't suggest anything
        registerMethodCallError(expression, PsiUtil.isLanguageLevel7OrHigher(expression), false);
      }
    }
  }

  private static class ReplaceWithObjectsNonNullFix extends InspectionGadgetsFix {
    private static class Replacer {
      private final Consumer<String> myReplacer;
      private final PsiExpression myNullComparedExpression;
      private final PsiExpression myMessage;

      private Replacer(@NotNull Consumer<String> replacer, @NotNull PsiExpression nullComparedExpression, @Nullable PsiExpression message) {
        myReplacer = replacer;
        myNullComparedExpression = nullComparedExpression;
        myMessage = message;
      }

      public void replace() {
        String messageText;
        if (myMessage == null) {
          messageText = "";
        }
        else {
          PsiType messageType = myMessage.getType();
          messageText = ", " + ((messageType != null && messageType.equalsToText(CommonClassNames.JAVA_LANG_STRING))
                                ? myMessage.getText()
                                : (CommonClassNames.JAVA_LANG_STRING + ".valueOf(" + myMessage.getText() + ")"));
        }
        String newText = "java.util.Objects.requireNonNull(" + myNullComparedExpression.getText() + messageText + ")";
        myReplacer.consume(newText);
      }
    }
    private final boolean myIsIfStatement;

    public ReplaceWithObjectsNonNullFix(boolean isIfStatement) {
      myIsIfStatement = isIfStatement;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      Replacer info = getReplaceInfo(descriptor);
      if (info == null) return;
      info.replace();
    }

    @Nullable
    private Replacer getReplaceInfo(ProblemDescriptor descriptor) {
      if (myIsIfStatement) {
        final PsiElement parent = descriptor.getPsiElement().getParent();
        if (!(parent instanceof PsiIfStatement)) return null;
        final PsiIfStatement ifStatement = (PsiIfStatement)parent;
        PsiExpression condition = ifStatement.getCondition();
        if (!(condition instanceof PsiBinaryExpression)) return null;
        PsiExpression nullComparedExpression = ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)condition);
        if (nullComparedExpression == null) return null;
        CommentTracker tracker = new CommentTracker();
        return new Replacer(text -> PsiReplacementUtil.replaceStatementAndShortenClassNames(ifStatement, text + ";", tracker),
                            tracker.markUnchanged(nullComparedExpression),
                            null);
      } else {
        PsiReferenceExpression ref = ObjectUtils.tryCast(descriptor.getPsiElement().getParent(), PsiReferenceExpression.class);
        if (ref == null) return null;
        PsiMethodCallExpression methodCall = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
        if (methodCall == null || !MethodCallUtils.isCallToMethod(methodCall,
                                                                  GUAVA_PRECONDITIONS,
                                                                  null,
                                                                  GUAVA_CHECK_NON_NULL,
                                                                  (PsiType[])null)) {
          return null;
        }
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length > 2) return null;
        CommentTracker tracker = new CommentTracker();
        return new Replacer(text -> PsiReplacementUtil.replaceExpressionAndShorten(methodCall, text, tracker),
                            tracker.markUnchanged(args[0]),
                            args.length == 2 ? tracker.markUnchanged(args[1]) : null);
      }
    }

  }

  private static class IfToAssertionFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.assertion.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement parent = descriptor.getPsiElement().getParent();
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      @NonNls final StringBuilder newStatementText = new StringBuilder("assert ");
      CommentTracker tracker = new CommentTracker();
      newStatementText.append(BoolUtils.getNegatedExpressionText(ifStatement.getCondition(), tracker));
      final PsiNewExpression newException = getThrownNewException(ifStatement.getThenBranch());
      final String message = getExceptionMessage(newException, tracker);
      if (message != null) {
        newStatementText.append(':').append(message);
      }
      newStatementText.append(';');
      PsiReplacementUtil.replaceStatement(ifStatement, newStatementText.toString(), tracker);
    }

    private static String getExceptionMessage(PsiNewExpression newExpression, CommentTracker tracker) {
      if (newExpression == null) {
        return null;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return null;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 1) {
        return null;
      }
      return tracker.text(arguments[0]);
    }
  }
}
