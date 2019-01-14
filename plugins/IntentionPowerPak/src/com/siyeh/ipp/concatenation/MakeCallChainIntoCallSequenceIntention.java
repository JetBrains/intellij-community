/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MakeCallChainIntoCallSequenceIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new MethodCallChainPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final List<String> callTexts = new ArrayList<>();
    PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
    if (call == null) return;
    call = RefactoringUtil.ensureCodeBlock(call);
    if (call == null) return;
    final PsiStatement appendStatement = ObjectUtils.tryCast(RefactoringUtil.getParentStatement(call, false), PsiStatement.class);
    if (appendStatement == null) return;
    PsiExpression toReplace = call;
    PsiExpression root = MethodCallChainPredicate.getCallChainRoot(call);
    if (root == null) return;
    PsiType callType = call.getType();
    if (callType == null) return;
    CommentTracker tracker = new CommentTracker();
    while (call != null && call != root) {
      callTexts.add(call.getMethodExpression().getReferenceName() + tracker.text(call.getArgumentList()));
      call = MethodCallUtils.getQualifierMethodCall(call);
    }
    final PsiType rootType = root.getType();
    if (rootType == null) return;
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(toReplace.getParent());
    
    // By default we introduce new variable and assign it to builder 
    String targetText = "x";
    boolean introduceVariable = true;
    boolean keepLastStatement = true;
    final String variableText = rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
    @NonNls String firstStatement = (JavaCodeStyleSettings.getInstance(toReplace.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "") 
                                    + variableText;
    // In some cases we can omit new variable reusing the existing one instead
    if (isSimpleReference(root)) {
      targetText = root.getText();
      firstStatement = null;
      introduceVariable = false;
    }
    else if (parent instanceof PsiAssignmentExpression && parent.getParent() instanceof PsiExpressionStatement &&
             ((PsiAssignmentExpression)parent).getOperationTokenType().equals(JavaTokenType.EQ)) {
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
      if (lhs instanceof PsiReferenceExpression) {
        final PsiReferenceExpression expression = (PsiReferenceExpression)lhs;
        final PsiElement target = expression.resolve();
        if (target instanceof PsiVariable) {
          final PsiVariable variable = (PsiVariable)target;
          final PsiType variableType = variable.getType();
          if (variableType.equals(rootType)) {
            targetText = tracker.text(lhs);
            firstStatement = targetText + '=' + root.getText() + ';';
            keepLastStatement = introduceVariable = false;
          }
        }
      }
    }
    else if (parent instanceof PsiLocalVariable) {
      final PsiLocalVariable variable = (PsiLocalVariable)parent;
      final PsiType variableType = variable.getType();
      if (variableType.equals(rootType)) {
        targetText = variable.getName();
        PsiLocalVariable varCopy = (PsiLocalVariable)variable.copy();
        varCopy.setInitializer(root);
        firstStatement = varCopy.getText();
        keepLastStatement = introduceVariable = false;
      }
    }
    if (parent instanceof PsiExpressionStatement) {
      keepLastStatement = false;
    }
    if (keepLastStatement && !callType.equals(rootType)) {
      callTexts.remove(0);
      toReplace = Objects.requireNonNull(((PsiMethodCallExpression)toReplace).getMethodExpression().getQualifierExpression());
    }
    String replacementBlock = generateReplacementBlock(callTexts, targetText, firstStatement);
    final PsiElement appendStatementParent = appendStatement.getParent();
    PsiVariable variable = appendStatements(appendStatement, tracker, introduceVariable, replacementBlock);
    if (keepLastStatement) {
      tracker.replaceAndRestoreComments(toReplace, targetText);
    } else {
      tracker.deleteAndRestoreComments(appendStatement);
    }
    if (variable != null) {
      HighlightUtil.showRenameTemplate(appendStatementParent, variable);
    }
  }

  @Nullable
  private static PsiVariable appendStatements(PsiStatement anchor,
                                              CommentTracker tracker,
                                              boolean introduceVariable,
                                              String replacementBlock) {
    PsiElement parent = anchor.getParent();
    Project project = anchor.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    PsiBlockStatement codeBlock = (PsiBlockStatement)factory.createStatementFromText(replacementBlock, anchor);
    PsiStatement[] statements = codeBlock.getCodeBlock().getStatements();
    PsiVariable variable = null;
    for (int i = 0, length = statements.length; i < length; i++) {
      final PsiElement insertedStatement = parent.addBefore(tracker.markUnchanged(statements[i]), anchor);
      if (i == 0 && introduceVariable) {
        variable = (PsiVariable)((PsiDeclarationStatement)insertedStatement).getDeclaredElements()[0];
      }
      codeStyleManager.reformat(insertedStatement);
    }
    return variable;
  }

  @NotNull
  private static String generateReplacementBlock(List<String> calls, String target, String firstStatement) {
    final StringBuilder builder = new StringBuilder("{\n");
    if (firstStatement != null) {
      builder.append(firstStatement);
    }
    Collections.reverse(calls);
    for (final String callText : calls) {
      builder.append(target).append('.').append(callText).append(";\n");
    }
    builder.append('}');
    return builder.toString();
  }

  @Contract("null -> false")
  private static boolean isSimpleReference(PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) return false;
    PsiReferenceExpression ref = (PsiReferenceExpression)expression;
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) {
      if (!(qualifier instanceof PsiQualifiedExpression) || ((PsiQualifiedExpression)qualifier).getQualifier() != null) return false; 
    }
    return ref.resolve() instanceof PsiVariable;
  }
}
