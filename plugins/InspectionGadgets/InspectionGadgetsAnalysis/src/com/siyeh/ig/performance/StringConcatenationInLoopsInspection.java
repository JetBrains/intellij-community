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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class StringConcatenationInLoopsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.display.name");
  }

  @org.intellij.lang.annotations.Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "StringConcatenationInLoop";
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

  static PsiLoopStatement getOutermostCommonLoop(PsiExpression expression, PsiVariable variable) {
    PsiElement stopAt = null;
    PsiCodeBlock block = StringConcatenationInLoopsVisitor.getSurroundingBlock(expression);
    if (block != null) {
      PsiElement ref;
      if (expression instanceof PsiAssignmentExpression) {
        ref = expression;
      }
      else {
        PsiReference reference = ReferencesSearch.search(variable, new LocalSearchScope(expression)).findFirst();
        ref = reference != null ? reference.getElement() : null;
      }
      if (ref != null) {
        PsiElement[] elements = StreamEx.of(DefUseUtil.getDefs(block, variable, expression)).prepend(expression).toArray(PsiElement[]::new);
        stopAt = PsiTreeUtil.findCommonParent(elements);
      }
    }
    PsiElement parent = expression.getParent();
    PsiLoopStatement commonLoop = null;
    while (parent != null && parent != stopAt && !(parent instanceof PsiMethod)
           && !(parent instanceof PsiClass) && !(parent instanceof PsiLambdaExpression)) {
      if (parent instanceof PsiLoopStatement) {
        commonLoop = (PsiLoopStatement)parent;
      }
      parent = parent.getParent();
    }
    return commonLoop;
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

      if (!checkExpression(expression)) return;

      if (ExpressionUtils.isEvaluatedAtCompileTime(expression)) return;

      if (!isAppendedRepeatedly(expression)) return;
      final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
      assert sign != null;
      registerError(sign, expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) return;

      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();

      if (!tokenType.equals(JavaTokenType.PLUSEQ)) return;

      if (!checkExpression(expression)) return;

      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());

      if (!(lhs instanceof PsiReferenceExpression)) return;
      registerError(sign, expression);
    }

    private static boolean checkExpression(PsiExpression expression) {
      if (!TypeUtils.isJavaLangString(expression.getType()) || ControlFlowUtils.isInExitStatement(expression) ||
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
          return commonLoop != null &&
                 !ControlFlowUtils.isExecutedOnceInLoop(PsiTreeUtil.getParentOfType(expression, PsiStatement.class), commonLoop) &&
                 !isUsedCompletely(variable, commonLoop);
        }
      }
      return false;
    }

    private static boolean isUsedCompletely(PsiVariable variable, PsiLoopStatement loop) {
      boolean notUsedCompletely = ReferencesSearch.search(variable, new LocalSearchScope(loop)).forEach(ref -> {
        PsiExpression expression = ObjectUtils.tryCast(ref.getElement(), PsiExpression.class);
        if (expression == null) return true;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        while (parent instanceof PsiTypeCastExpression || parent instanceof PsiConditionalExpression) {
          parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        }
        if (parent instanceof PsiExpressionList ||
            (parent instanceof PsiAssignmentExpression &&
             PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getRExpression(), expression, false))) {
          PsiStatement statement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
          return ControlFlowUtils.isExecutedOnceInLoop(statement, loop) || ControlFlowUtils.isVariableReassigned(statement, variable);
        }
        return true;
      });
      return !notUsedCompletely;
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
      final PsiExpression rhs = assignmentExpression.getRExpression();
      return isAppended(referenceExpression, rhs);
    }

    private static boolean isAppended(PsiReferenceExpression otherRef, PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if(expression instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        if (polyadicExpression.getOperationTokenType().equals(JavaTokenType.PLUS)) {
          for (PsiExpression operand : polyadicExpression.getOperands()) {
            if (isSameReference(operand, otherRef) || isAppended(otherRef, operand)) return true;
          }
        }
      }
      return false;
    }

    private static boolean isSameReference(PsiExpression operand, PsiReferenceExpression ref) {
      PsiReferenceExpression other = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiReferenceExpression.class);
      if (other == null) {
        return false;
      }
      String name = other.getReferenceName();
      if (name == null || !name.equals(ref.getReferenceName())) return false;
      PsiExpression qualifier = ref.getQualifierExpression();
      PsiExpression otherQualifier = other.getQualifierExpression();
      if (qualifier == null && otherQualifier == null) return true;
      if (qualifier == null && ref.resolve() instanceof PsiField) {
        qualifier = ExpressionUtils.getQualifierOrThis(ref);
      }
      if (otherQualifier == null && other.resolve() instanceof PsiField) {
        otherQualifier = ExpressionUtils.getQualifierOrThis(other);
      }
      if (qualifier == null || otherQualifier == null) return false;
      if (qualifier instanceof PsiReferenceExpression) {
        return isSameReference(otherQualifier, (PsiReferenceExpression)qualifier);
      }
      return PsiEquivalenceUtil.areElementsEquivalent(qualifier, otherQualifier);
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

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    PsiExpression expression = ObjectUtils.tryCast(ArrayUtil.getFirstElement(infos), PsiExpression.class);
    PsiVariable var = getAppendedVariable(expression);
    if (var == null) return InspectionGadgetsFix.EMPTY_ARRAY;
    List<InspectionGadgetsFix> fixes = new ArrayList<>();
    if (var instanceof PsiLocalVariable) {
      fixes.add(new ReplaceWithStringBuilderFix(var));
      PsiLoopStatement loop = getOutermostCommonLoop(expression, var);
      // Do not add IntroduceStringBuilderFix if there's only 0 or 1 reference to the variable outside loop:
      // in this case the result is usually similar to ReplaceWithStringBuilderFix or worse
      if (ReferencesSearch.search(var).findAll().stream()
            .map(PsiReference::getElement).filter(e -> !PsiTreeUtil.isAncestor(loop, e, true))
            .limit(2).count() > 1) {
        fixes.add(new IntroduceStringBuilderFix(var));
      }
    }
    else if (var instanceof PsiParameter) {
      fixes.add(new IntroduceStringBuilderFix(var));
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  static abstract class AbstractStringBuilderFix extends InspectionGadgetsFix {
    static final Pattern PRINT_OR_PRINTLN = Pattern.compile("print|println");

    String myName;
    String myTargetType;

    public AbstractStringBuilderFix(PsiVariable variable) {
      myName = variable.getName();
      myTargetType = PsiUtil.isLanguageLevel5OrHigher(variable) ?
                     CommonClassNames.JAVA_LANG_STRING_BUILDER : CommonClassNames.JAVA_LANG_STRING_BUFFER;
    }

    @NotNull
    String generateNewStringBuilder(PsiExpression initializer, CommentTracker ct) {
      if(ExpressionUtils.isNullLiteral(initializer)) {
        return ct.text(initializer);
      }
      String text = initializer == null || ExpressionUtils.isLiteral(initializer, "") ? "" : ct.text(initializer);
      return "new " + myTargetType + "(" + text + ")";
    }

    void replaceAll(PsiVariable variable,
                    PsiVariable builderVariable,
                    PsiElement scope,
                    CommentTracker ct) {
      replaceAll(variable, builderVariable, scope, ct, ref -> false);
    }

    void replaceAll(PsiVariable variable,
                    PsiVariable builderVariable,
                    PsiElement scope,
                    CommentTracker ct,
                    Predicate<PsiReferenceExpression> skip) {
      Query<PsiReference> query =
        scope == null ? ReferencesSearch.search(variable) : ReferencesSearch.search(variable, new LocalSearchScope(scope));
      Collection<PsiReference> refs = query.findAll();
      for(PsiReference ref : refs) {
        PsiElement target = ref.getElement();
        if(target instanceof PsiReferenceExpression && target.isValid() && !skip.test((PsiReferenceExpression)target)) {
          replace(variable, builderVariable, (PsiReferenceExpression)target, ct);
        }
      }
    }

    private void replace(PsiVariable variable,
                         PsiVariable builderVariable,
                         PsiReferenceExpression ref,
                         CommentTracker ct) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if(parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) == ref) {
          replaceInAssignment(variable, builderVariable, assignment, ct);
          return;
        } else {
          // ref is r-value
          if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) return;
        }
      }
      if (variable != builderVariable) {
        ExpressionUtils.bindReferenceTo(ref, Objects.requireNonNull(builderVariable.getName()));
      }
      PsiMethodCallExpression methodCallExpression = ExpressionUtils.getCallForQualifier(ref);
      if(methodCallExpression != null) {
        replaceInCallQualifier(builderVariable, methodCallExpression, ct);
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
      ct.replace(ref, builderVariable.getName() + ".toString()");
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

    private static void replaceInCallQualifier(PsiVariable variable, PsiMethodCallExpression call, CommentTracker ct) {
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
            ct.replace(toReplace, emptyCheck);
            return;
          }
          default:
        }
      }
      PsiExpression qualifier = Objects.requireNonNull(call.getMethodExpression().getQualifierExpression());
      ct.replace(qualifier, variable.getName() + ".toString()");
    }

    private void replaceInAssignment(PsiVariable variable,
                                     PsiVariable builderVariable,
                                     PsiAssignmentExpression assignment,
                                     CommentTracker ct) {
      PsiExpression rValue = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
      String builderName = Objects.requireNonNull(builderVariable.getName());
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        if (rValue instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)rValue).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiPolyadicExpression concat = (PsiPolyadicExpression)rValue;
          PsiExpression[] operands = concat.getOperands();
          if (operands.length > 1) {
            // s = s + ...;
            if (ExpressionUtils.isReferenceTo(operands[0], variable)) {
              StreamEx.iterate(operands[1], Objects::nonNull, PsiElement::getNextSibling).forEach(ct::markUnchanged);
              replaceAll(variable, builderVariable, rValue, ct, operands[0]::equals);
              StringBuilder replacement =
                ChangeToAppendUtil.buildAppendExpression(rValue, false, new StringBuilder(builderName));
              if (replacement != null) {
                PsiMethodCallExpression append = (PsiMethodCallExpression)ct.replace(assignment, replacement.toString());
                while(true) {
                  PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(append);
                  if (qualifierCall == null) break;
                  append = qualifierCall;
                }
                PsiExpression qualifier = append.getMethodExpression().getQualifierExpression();
                if (qualifier != null) {
                  append.replace(qualifier);
                }
              }
              return;
            }
            // s = ... + s;
            PsiExpression lastOp = operands[operands.length - 1];
            if (ExpressionUtils.isReferenceTo(lastOp, variable)) {
              ct.delete(concat.getTokenBeforeOperand(lastOp), lastOp);
              replaceAll(variable, builderVariable, rValue, ct);
              ct.replace(assignment, builderName + ".insert(0," + ct.text(rValue) + ")");
              return;
            }
          }
        }
      }
      if(rValue != null) {
        replaceAll(variable, builderVariable, rValue, ct);
        rValue = assignment.getRExpression();
      }
      if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // s += ...;
        String replacement = "";
        if (rValue != null) {
          StringBuilder sb =
            ChangeToAppendUtil.buildAppendExpression(ct.markUnchanged(rValue), false, new StringBuilder(builderName));
          if (sb != null) {
            replacement = sb.toString();
          }
        }
        ct.replace(assignment, replacement);
      } else if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        ct.replace(assignment, builderName + "=" + generateNewStringBuilder(rValue, ct));
      }
    }
  }

  static class IntroduceStringBuilderFix extends AbstractStringBuilderFix {
    public IntroduceStringBuilderFix(PsiVariable variable) {
      super(variable);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiExpression expression = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiExpression.class);
      if (expression == null) return;
      PsiVariable variable = getAppendedVariable(expression);
      if (variable == null) return;
      PsiLoopStatement loop = getOutermostCommonLoop(expression, variable);
      if (loop == null) return;
      ControlFlowUtils.InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(variable, loop);
      String newName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(variable.getName() + "Builder", loop, true);
      String newStringBuilder =
        myTargetType + " " + newName + "=new " + myTargetType + "(" + variable.getName() + ");";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      Object marker = new Object();
      PsiTreeUtil.mark(loop, marker);
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)BlockUtils.addBefore(loop, factory.createStatementFromText(newStringBuilder, loop));
      if (!loop.isValid()) {
        loop = (PsiLoopStatement)PsiTreeUtil.releaseMark(declaration.getParent(), marker);
        if (loop == null) return;
      }
      PsiVariable builderVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      PsiExpression builderInitializer = Objects.requireNonNull(builderVariable.getInitializer());
      CommentTracker ct = new CommentTracker();
      replaceAll(variable, builderVariable, loop, ct);
      String toString = variable.getName() + " = " + newName + ".toString();";

      PsiExpression initializer = variable.getInitializer();
      switch (status) {
        case DECLARED_JUST_BEFORE:
          // Put original variable declaration after the loop and use its original initializer in StringBuilder constructor
          PsiTypeElement typeElement = variable.getTypeElement();
          if (typeElement != null && initializer != null) {
            ct.replace(builderInitializer, generateNewStringBuilder(initializer, ct));
            ct.replace(initializer, newName + ".toString()");
            toString = variable.getText();
            ct.delete(variable);
          }
          break;
        case AT_WANTED_PLACE_ONLY:
          // Move original initializer to the StringBuilder constructor
          if (initializer != null) {
            ct.replace(builderInitializer, generateNewStringBuilder(initializer, ct));
            initializer.delete();
          }
          break;
        case AT_WANTED_PLACE:
          // Copy original initializer to the StringBuilder constructor if possible
          if (ExpressionUtils.isSimpleExpression(initializer)) {
            ct.replace(builderInitializer, generateNewStringBuilder(initializer, ct));
          }
          break;
        case UNKNOWN:
          PsiElement prevStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(declaration);
          PsiExpression prevAssignment = ExpressionUtils.getAssignmentTo(prevStatement, variable);
          if (prevAssignment != null) {
            ct.replace(builderInitializer, generateNewStringBuilder(prevAssignment, ct));
            ct.delete(prevStatement);
          }
          break;
      }
      BlockUtils.addAfter(loop, factory.createStatementFromText(toString, loop));
      ct.insertCommentsBefore(loop);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.introduce.fix.name", myName, StringUtil.getShortName(myTargetType));
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.introduce.fix");
    }
  }

  static class ReplaceWithStringBuilderFix extends AbstractStringBuilderFix {
    public ReplaceWithStringBuilderFix(PsiVariable variable) {
      super(variable);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiExpression expression = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiExpression.class);
      if (expression == null) return;
      PsiVariable variable = getAppendedVariable(expression);
      if (!(variable instanceof PsiLocalVariable)) return;
      variable.normalizeDeclaration();
      PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) return;
      CommentTracker ct = new CommentTracker();
      replaceAll(variable, variable, null, ct);
      ct.replace(typeElement, myTargetType);
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        ct.replace(initializer, generateNewStringBuilder(initializer, ct));
      }
      PsiStatement commentPlace = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
      ct.insertCommentsBefore(commentPlace == null ? variable : commentPlace);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("string.concatenation.replace.fix.name", myName, StringUtil.getShortName(myTargetType));
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.replace.fix");
    }
  }
}
