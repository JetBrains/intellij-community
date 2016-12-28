/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class StringConcatenationInLoopsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInLoopsVisitor();
  }

  private static class StringConcatenationInLoopsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length <= 1) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) return;

      if (!checkExpression(expression, expression.getType())) return;

      if (ExpressionUtils.isEvaluatedAtCompileTime(expression)) return;

      if (!isAppendedRepeatedly(expression)) return;
      final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
      assert sign != null;
      registerError(sign, getAppendedVariable(expression));
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) return;

      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();

      if (!tokenType.equals(JavaTokenType.PLUSEQ)) return;

      PsiExpression lhs = expression.getLExpression();

      if (!checkExpression(expression, lhs.getType())) return;

      lhs = PsiUtil.skipParenthesizedExprDown(lhs);
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      registerError(sign, getAppendedVariable(expression));
    }

    private boolean checkExpression(PsiExpression expression, PsiType type) {
      if (!TypeUtils.isJavaLangString(type) || ControlFlowUtils.isInExitStatement(expression) ||
          !ControlFlowUtils.isInLoop(expression)) return false;

      PsiElement parent = expression;
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (parent != expression && parent instanceof PsiAssignmentExpression &&
          ((PsiAssignmentExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // Will be reported for parent +=, no need to report twice
        return false;
      }

      if (parent instanceof PsiAssignmentExpression) {
        expression = (PsiExpression)parent;
        PsiVariable variable = getAppendedVariable(expression);

        if (variable != null) {
          PsiLoopStatement commonLoop = getOutermostCommonLoop(expression, variable);
          return commonLoop != null && !flowBreaksLoop(PsiTreeUtil.getParentOfType(expression, PsiStatement.class), commonLoop);
        }
      }
      return !containingStatementExits(expression);
    }

    @Contract("null, _ -> false")
    private static boolean flowBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
      if(statement == null || statement == loop) return false;
      for(PsiStatement sibling = statement; sibling != null; sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class)) {
        if(sibling instanceof PsiContinueStatement) return false;
        if(sibling instanceof PsiThrowStatement || sibling instanceof PsiReturnStatement) return true;
        if(sibling instanceof PsiBreakStatement) {
          PsiBreakStatement breakStatement = (PsiBreakStatement)sibling;
          PsiStatement exitedStatement = breakStatement.findExitedStatement();
          if(exitedStatement == loop) return true;
          return flowBreaksLoop(exitedStatement, loop);
        }
      }
      PsiElement parent = statement.getParent();
      if(parent == loop) return false;
      if(parent instanceof PsiCodeBlock) {
        PsiElement gParent = parent.getParent();
        if(gParent instanceof PsiBlockStatement || gParent instanceof PsiSwitchStatement) {
          return flowBreaksLoop((PsiStatement)gParent, loop);
        }
        return false;
      }
      if(parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement || parent instanceof PsiSwitchLabelStatement
        || parent instanceof PsiSwitchStatement) {
        return flowBreaksLoop((PsiStatement)parent, loop);
      }
      return false;
    }

    private PsiLoopStatement getOutermostCommonLoop(PsiExpression expression, PsiVariable variable) {
      PsiElement stopAt = null;
      PsiCodeBlock block = getSurroundingBlock(expression);
      if(block != null) {
        PsiElement ref;
        if(expression instanceof PsiAssignmentExpression) {
          ref = expression;
        } else {
          PsiReference reference = ReferencesSearch.search(variable, new LocalSearchScope(expression)).findFirst();
          ref = reference != null ? reference.getElement() : null;
        }
        if(ref != null) {
          PsiElement[] elements = StreamEx.of(DefUseUtil.getDefs(block, variable, expression)).prepend(expression).toArray(PsiElement[]::new);
          stopAt = PsiTreeUtil.findCommonParent(elements);
        }
      }
      PsiElement parent = expression.getParent();
      PsiLoopStatement commonLoop = null;
      while(parent != null && parent != stopAt && !(parent instanceof PsiMethod)
            && !(parent instanceof PsiClass) && !(parent instanceof PsiLambdaExpression)) {
        if(parent instanceof PsiLoopStatement) {
          commonLoop = (PsiLoopStatement)parent;
        }
        parent = parent.getParent();
      }
      return commonLoop;
    }

    @Nullable
    private static PsiCodeBlock getSurroundingBlock(PsiElement expression) {
      PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiClassInitializer.class, PsiLambdaExpression.class);
      if(parent instanceof PsiMethod) {
        return ((PsiMethod)parent).getBody();
      } else if(parent instanceof PsiClassInitializer) {
        return ((PsiClassInitializer)parent).getBody();
      } else if(parent instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)parent).getBody();
        if(body instanceof PsiCodeBlock) {
          return (PsiCodeBlock)body;
        }
      }
      return null;
    }

    private static boolean containingStatementExits(PsiElement element) {
      final PsiStatement newExpressionStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (newExpressionStatement == null) {
        return false;
      }
      final PsiStatement parentStatement = PsiTreeUtil.getParentOfType(newExpressionStatement, PsiStatement.class);
      return !ControlFlowUtils.statementMayCompleteNormally(parentStatement);
    }

    private static boolean isAppendedRepeatedly(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      if (assignmentExpression.getOperationTokenType() == JavaTokenType.PLUSEQ) {
        return true;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      return isAppended(variable, rhs);
    }

    private static boolean isAppended(PsiVariable variable, PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if(expression instanceof PsiPolyadicExpression) {
        for(PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
          if(ExpressionUtils.isReferenceTo(operand, variable) || isAppended(variable, operand)) return true;
        }
      }
      return false;
    }
  }

  @Contract("null -> null")
  @Nullable
  private static PsiVariable getAppendedVariable(PsiExpression expression) {
    PsiElement parent = expression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getLExpression());
    if (!(lhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiElement element = ((PsiReferenceExpression)lhs).resolve();
    return element instanceof PsiVariable ? (PsiVariable)element : null;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return (infos.length > 0 && infos[0] instanceof PsiLocalVariable) ? new ReplaceWithStringBuilderFix((PsiVariable)infos[0]) : null;
  }

  static class ReplaceWithStringBuilderFix extends InspectionGadgetsFix {
    private static final Pattern PRINT_OR_PRINTLN = Pattern.compile("print|println");

    private String myName;
    private String myTargetType;

    public ReplaceWithStringBuilderFix(PsiVariable variable) {
      myName = variable.getName();
      myTargetType = PsiUtil.isLanguageLevel5OrHigher(variable) ? "StringBuilder" : "StringBuffer";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiExpression expression = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiExpression.class);
      if (expression == null) return;
      PsiVariable variable = getAppendedVariable(expression);
      if(!(variable instanceof PsiLocalVariable)) return;
      variable.normalizeDeclaration();
      PsiTypeElement typeElement = variable.getTypeElement();
      if(typeElement == null) return;
      List<PsiElement> results = new ArrayList<>();
      CommentTracker ct = new CommentTracker();
      replaceAll(variable, null, results, ct);
      results.add(ct.replace(typeElement, "java.lang." + myTargetType));
      PsiExpression initializer = variable.getInitializer();
      if(initializer != null) {
        results.add(ct.replace(initializer, generateNewStringBuilder(initializer, ct)));
      }
      PsiStatement commentPlace = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
      ct.insertCommentsBefore(commentPlace == null ? variable : commentPlace);
      for(PsiElement result : results) {
        if(result.isValid()) {
          result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
          CodeStyleManager.getInstance(project).reformat(result);
        }
      }
    }

    @NotNull
    private String generateNewStringBuilder(PsiExpression initializer, CommentTracker ct) {
      if(ExpressionUtils.isNullLiteral(initializer)) {
        return ct.text(initializer);
      }
      String text = initializer == null || ExpressionUtils.isLiteral(initializer, "") ? "" : ct.text(initializer);
      return "new java.lang." + myTargetType + "(" + text + ")";
    }

    private void replaceAll(PsiVariable variable,
                            PsiElement scope,
                            List<PsiElement> results,
                            CommentTracker ct) {
      Query<PsiReference> query =
        scope == null ? ReferencesSearch.search(variable) : ReferencesSearch.search(variable, new LocalSearchScope(scope));
      Collection<PsiReference> refs = query.findAll();
      for(PsiReference ref : refs) {
        PsiElement target = ref.getElement();
        if(target instanceof PsiReferenceExpression && target.isValid()) {
          replace(variable, results, (PsiReferenceExpression)target, ct);
        }
      }
    }

    private void replace(PsiVariable variable, List<PsiElement> results, PsiReferenceExpression ref, CommentTracker ct) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if(parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) == ref) {
          replaceInAssignment(variable, results, assignment, ct);
          return;
        } else {
          // ref is r-value
          if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) return;
        }
      }
      PsiMethodCallExpression methodCallExpression = ExpressionUtils.getCallForQualifier(ref);
      if(methodCallExpression != null) {
        replaceInCallQualifier(variable, results, methodCallExpression, ct);
        return;
      }
      if(parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
        PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
        if(expressions.length == 1 && expressions[0] == ref) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)parent.getParent();
          if(canAcceptBuilderInsteadOfString(call)) {
            return;
          }
        }
      }
      if(parent instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)parent;
        if(ExpressionUtils.getValueComparedWithNull(binOp) != null) {
          return;
        }
      }
      if(parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUS)) {
        PsiExpression[] operands = ((PsiPolyadicExpression)parent).getOperands();
        for (PsiExpression operand : operands) {
          if (operand == ref) break;
          if (TypeUtils.isJavaLangString(operand.getType())) return;
        }
        if (operands.length > 1 && operands[0] == ref && TypeUtils.isJavaLangString(operands[1].getType())) return;
      }
      results.add(ct.replace(ref, variable.getName()+".toString()"));
    }

    private static boolean canAcceptBuilderInsteadOfString(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_LANG_STRING_BUILDER, null, "append",
                                            (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_LANG_STRING_BUFFER, null, "append",
                                        (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, "java.io.PrintStream", null, PRINT_OR_PRINTLN,
                                        (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, "java.io.PrintWriter", null, PRINT_OR_PRINTLN,
                                        (PsiType[])null);
    }

    private static void replaceInCallQualifier(PsiVariable variable,
                                               List<PsiElement> results,
                                               PsiMethodCallExpression call,
                                               CommentTracker ct) {
      PsiMethod method = call.resolveMethod();
      if(method != null) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        String name = method.getName();
        switch(name) {
          case "length":
          case "chars":
          case "codePoints":
          case "charAt":
          case "codePointAt":
          case "codePointBefore":
          case "codePointAfter":
          case "codePointCount":
          case "offsetByCodePoints":
          case "substring":
          case "subSequence":
            return;
          case "getChars":
            if(args.length == 4) return;
            break;
          case "indexOf":
          case "lastIndexOf":
            if(args.length >= 1 && args.length <= 2 && TypeUtils.isJavaLangString(args[0].getType())) return;
            break;
          case "isEmpty": {
            String sign = "==";
            PsiExpression negation = BoolUtils.findNegation(call);
            PsiElement toReplace = call;
            if (negation != null) {
              sign = ">";
              toReplace = negation;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
            PsiExpression emptyCheck = factory.createExpressionFromText(variable.getName() + ".length()" + sign + "0", call);
            PsiElement callParent = toReplace.getParent();
            if (callParent instanceof PsiExpression &&
                ParenthesesUtils.areParenthesesNeeded(emptyCheck, (PsiExpression)callParent, true)) {
              emptyCheck = factory.createExpressionFromText("(" + emptyCheck.getText() + ")", call);
            }
            results.add(ct.replace(toReplace, emptyCheck));
            return;
          }
          default:
        }
      }
      PsiExpression qualifier = Objects.requireNonNull(call.getMethodExpression().getQualifierExpression());
      results.add(ct.replace(qualifier, variable.getName() + ".toString()"));
    }

    private void replaceInAssignment(PsiVariable variable,
                                        List<PsiElement> results,
                                        PsiAssignmentExpression assignment,
                                        CommentTracker ct) {
      PsiExpression rValue = assignment.getRExpression();
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        if (rValue instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)rValue).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiPolyadicExpression concat = (PsiPolyadicExpression)rValue;
          PsiExpression[] operands = concat.getOperands();
          if (operands.length > 1) {
            // s = s + ...;
            if (ExpressionUtils.isReferenceTo(operands[0], variable)) {
              ct.delete(concat.getTokenBeforeOperand(operands[1]), operands[0]);
              replaceAll(variable, rValue, results, ct);
              results.add(ct.replace(assignment, variable.getName() + ".append(" + ct.text(rValue) + ")"));
              return;
            }
            // s = ... + s;
            PsiExpression lastOp = operands[operands.length - 1];
            if (ExpressionUtils.isReferenceTo(lastOp, variable)) {
              ct.delete(concat.getTokenBeforeOperand(lastOp), lastOp);
              replaceAll(variable, rValue, results, ct);
              results.add(ct.replace(assignment, variable.getName() + ".insert(0," + ct.text(rValue) + ")"));
              return;
            }
          }
        }
      }
      if(rValue != null) {
        replaceAll(variable, rValue, results, ct);
        rValue = assignment.getRExpression();
      }
      if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // s += ...;
        results.add(ct.replace(assignment, variable.getName() + ".append(" + ((rValue == null) ? "" : ct.text(rValue)) + ")"));
      } else if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        results.add(ct.replace(assignment, variable.getName() + "=" + generateNewStringBuilder(rValue, ct)));
      }
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.replace.fix.name", myName, myTargetType);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.replace.fix");
    }
  }
}
