// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.intention.impl.SplitConditionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A facility to ensure that there's a code block that surrounds given expression, so if parts of expression should be extracted
 * to separate statements, these is a place where new statements could be generated.
 * <p>
 * The lifecycle is the following:
 * <ol>
 *   <li>Inside the read action call {@link #forExpression(PsiExpression)} passing the expression you want to process.</li>
 *   <li>If it returns null, then there's no suitable code block and it cannot be added due to complex control-flow, language limitations, etc.</li>
 *   <li>Otherwise create a write action and call the {@link #surround()}. It's guaranteed to be successful.</li>
 *   <li>Use {@link SurroundResult#getAnchor()} to insert new statement before it. Use {@link SurroundResult#getExpression()} to find the 
 *   copy of original expression that appears in the resulting code. Note that the original expression itself may become invalid after 
 *   the {@link #surround()} call.</li>
 * </ol>
 * Note that {@link #forExpression(PsiExpression)} and {@link #surround()} should be called within the same read action.
 */
public abstract class CodeBlockSurrounder {
  private enum ParentContext {
    /**
     * The execution will terminate abruptly returning/throwing this expression as a result 
     * (return/yield/throw statement)
     */
    RETURN,
    /**
     * The value of the expression will be assigned to some variable (variable initializer or assignment expression)
     */
    ASSIGNMENT,
    /**
     * The expression result will be ignored (expression statement)
     */
    EXPRESSION,
    /**
     * The expression result is condition of simple 'if' statement (without 'else')
     */
    SIMPLE_IF_CONDITION,
    /**
     * Unknown
     */
    UNKNOWN
  }

  /**
   * Result of surrounding
   */
  public static class SurroundResult {
    private final @NotNull PsiExpression myExpression;
    private final @NotNull PsiStatement myAnchor;

    SurroundResult(@NotNull PsiExpression expression, @NotNull PsiStatement anchor) {
      myExpression = expression;
      myAnchor = anchor;
    }

    /**
     * @return a copy of original expression after the surrounding
     */
    public @NotNull PsiExpression getExpression() {
      return myExpression;
    }

    /**
     * @return an anchor statement: it's safe to add new statements before the anchor
     */
    public @NotNull PsiStatement getAnchor() {
      return myAnchor;
    }
  }

  final @NotNull PsiExpression myExpression;

  CodeBlockSurrounder(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  /**
   * @return the expected expression parent type after the replacement. Guaranteed to work only before {@link #surround()} call.
   * No write action is performed.
   */
  @NotNull ParentContext getExpectedParentContext() {
    PsiElement parent = myExpression.getParent();
    if (parent instanceof PsiAssignmentExpression && parent.getParent() instanceof PsiExpressionStatement &&
        !(parent.getParent().getParent() instanceof PsiSwitchLabeledRuleStatement) &&
        ((PsiAssignmentExpression)parent).getRExpression() == myExpression) {
      return ParentContext.ASSIGNMENT;
    }
    if (parent instanceof PsiLocalVariable) {
      PsiLocalVariable var = (PsiLocalVariable)parent;
      if (!var.getTypeElement().isInferredType() || PsiTypesUtil.isDenotableType(var.getType(), parent)) {
        return ParentContext.ASSIGNMENT;
      }
    }
    if (parent instanceof PsiReturnStatement || parent instanceof PsiYieldStatement || parent instanceof PsiThrowStatement) {
      return ParentContext.RETURN;
    }
    if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() == null &&
        ((PsiIfStatement)parent).getThenBranch() != null) {
      return ParentContext.SIMPLE_IF_CONDITION;
    }
    return ParentContext.UNKNOWN;
  }

  /**
   * Performs the refactoring ensuring that the expression is surrounded with the code block now.
   * Must be called at most once. Modifiers the PSI,
   * thus requires write action if applied to physical PSI.
   *
   * @return the expression that replaced the original expression
   */
  public @NotNull CodeBlockSurrounder.SurroundResult surround() {
    Object marker = new Object();
    PsiTreeUtil.mark(myExpression, marker);
    Project project = myExpression.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    boolean physical = myExpression.isPhysical();
    PsiStatement replacement = replace(project, factory);
    assert replacement.isPhysical() == physical;
    PsiExpression newExpression = Objects.requireNonNull((PsiExpression)PsiTreeUtil.releaseMark(replacement, marker));
    return new SurroundResult(newExpression, replacement);
  }

  @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
    throw new UnsupportedOperationException();
  }

  /**
   * @param expression expression to test
   * @return true if expression can be surrounded with a code block or already has a surrounding code block
   */
  public static boolean canSurround(@NotNull PsiExpression expression) {
    return forExpression(expression) != null;
  }

  /**
   * Creates a surrounder for given expression.
   * 
   * @param expression an expression to surround.
   * @return a new surrounder that is definitely capable to produce a code block around given expression 
   * where it's safe to place new statements. Returns null if it's impossible to surround given expression 
   * with a code block.
   */
  public static @Nullable CodeBlockSurrounder forExpression(@NotNull PsiExpression expression) {
    PsiElement cur = expression;
    PsiElement parent = cur.getParent();
    while (parent instanceof PsiExpression || parent instanceof PsiExpressionList) {
      if (parent instanceof PsiLambdaExpression) {
        return new LambdaCodeBlockSurrounder(expression, (PsiLambdaExpression)parent);
      }
      if (parent instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        IElementType type = polyadicExpression.getOperationTokenType();
        if (type.equals(JavaTokenType.ANDAND) && polyadicExpression.getOperands()[0] != cur) {
          PsiElement conditionParent = PsiUtil.skipParenthesizedExprUp(polyadicExpression.getParent());
          if (conditionParent instanceof PsiWhileStatement) {
            return new WhileConditionSurrounder(expression, (PsiWhileStatement)conditionParent);
          }
          CodeBlockSurrounder parentSurrounder = forExpressionSkipParentheses(polyadicExpression);
          if (parentSurrounder == null) return null;
          ParentContext parentContext = parentSurrounder.getExpectedParentContext();
          if (parentContext != ParentContext.RETURN && parentContext != ParentContext.SIMPLE_IF_CONDITION) return null;
          return new AndOrToIfSurrounder(expression, polyadicExpression, parentSurrounder);
        }
        else if (type.equals(JavaTokenType.OROR) && polyadicExpression.getOperands()[0] != cur) {
          CodeBlockSurrounder parentSurrounder = forExpressionSkipParentheses(polyadicExpression);
          if (parentSurrounder == null) return null;
          ParentContext parentContext = parentSurrounder.getExpectedParentContext();
          if (parentContext != ParentContext.RETURN) return null;
          return new AndOrToIfSurrounder(expression, polyadicExpression, parentSurrounder);
        }
      }
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != cur) {
        CodeBlockSurrounder parentSurrounder = forExpressionSkipParentheses((PsiConditionalExpression)parent);
        if (parentSurrounder == null) return null;
        ParentContext parentContext = parentSurrounder.getExpectedParentContext();
        if (parentContext != ParentContext.ASSIGNMENT && parentContext != ParentContext.RETURN) return null;
        return new TernaryToIfSurrounder(expression, (PsiConditionalExpression)parent, parentSurrounder);
      }
      if (JavaPsiConstructorUtil.isConstructorCall(parent)) {
        return null;
      }
      cur = parent;
      parent = cur.getParent();
    }
    if (parent instanceof PsiStatement) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiForStatement && ((PsiForStatement)grandParent).getUpdate() == parent) return null;
    }
    if (parent instanceof PsiWhileStatement && ((PsiWhileStatement)parent).getCondition() == cur) {
      return new WhileConditionSurrounder(expression, (PsiWhileStatement)parent);
    }
    if (parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == cur ||
        parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == cur ||
        parent instanceof PsiReturnStatement || parent instanceof PsiExpressionStatement ||
        parent instanceof PsiYieldStatement || parent instanceof PsiThrowStatement) {
      return forStatement((PsiStatement)parent, expression);
    }
    if (parent instanceof PsiLocalVariable) {
      PsiDeclarationStatement decl = ObjectUtils.tryCast(parent.getParent(), PsiDeclarationStatement.class);
      if (decl != null && ArrayUtil.getFirstElement(decl.getDeclaredElements()) == parent) {
        PsiTypeElement typeElement = ((PsiLocalVariable)parent).getTypeElement();
        if (!typeElement.isInferredType() ||
            PsiTypesUtil.replaceWithExplicitType(((PsiLocalVariable)parent.copy()).getTypeElement()) != null) {
          PsiElement declParent = decl.getParent();
          if (declParent instanceof PsiForStatement && ((PsiForStatement)declParent).getInitialization() == decl) {
            if (hasNameCollision(decl, declParent.getParent())) {
              // There's another var with the same name as one declared in for initialization 
              return new SimpleSurrounder(expression, (PsiForStatement)declParent);
            }
            return forStatement((PsiStatement)declParent, expression);
          }
          return forStatement(decl, expression);
        }
      }
    }
    if (parent instanceof PsiResourceVariable) {
      PsiResourceList list = ObjectUtils.tryCast(parent.getParent(), PsiResourceList.class);
      if (list != null && list.getParent() instanceof PsiTryStatement) {
        return new SplitTrySurrounder(expression, (PsiResourceVariable)parent, (PsiTryStatement)list.getParent());
      }
      return null;
    }
    if (parent instanceof PsiField) {
      return new ExtractFieldInitializerSurrounder(expression, (PsiField)parent);
    }

    return null;
  }

  private static @Nullable CodeBlockSurrounder forExpressionSkipParentheses(PsiExpression expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    return forExpression(expression);
  }

  private static boolean hasNameCollision(PsiElement declaration, PsiElement context) {
    if (declaration instanceof PsiDeclarationStatement) {
      PsiResolveHelper helper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
      return StreamEx.of(((PsiDeclarationStatement)declaration).getDeclaredElements())
        .select(PsiLocalVariable.class)
        .map(PsiLocalVariable::getName)
        .nonNull()
        .anyMatch(name -> helper.resolveAccessibleReferencedVariable(name, context) != null);
    }
    return false;
  }

  private static CodeBlockSurrounder forStatement(PsiStatement statement, PsiExpression expression) {
    PsiElement statementParent = statement.getParent();
    if (statementParent instanceof PsiLabeledStatement || statementParent instanceof PsiForStatement) {
      statement = (PsiStatement)statementParent;
      statementParent = statement.getParent();
    }
    if (statementParent instanceof PsiCodeBlock) {
      return new NoOpSurrounder(expression, statement);
    }
    if (statement instanceof PsiExpressionStatement && statementParent instanceof PsiSwitchLabeledRuleStatement &&
        ((PsiSwitchLabeledRuleStatement)statementParent).getEnclosingSwitchBlock() instanceof PsiSwitchExpression) {
      return new YieldSurrounder(expression, (PsiExpressionStatement)statement);
    }
    return new SimpleSurrounder(expression, statement);
  }

  private static class NoOpSurrounder extends CodeBlockSurrounder {
    private final PsiStatement myAnchor;

    NoOpSurrounder(@NotNull PsiExpression expression, @NotNull PsiStatement anchor) {
      super(expression);
      myAnchor = anchor;
    }

    @Override
    public @NotNull CodeBlockSurrounder.SurroundResult surround() {
      return new SurroundResult(myExpression, myAnchor);
    }
  }


  private static class LambdaCodeBlockSurrounder extends CodeBlockSurrounder {
    private final @NotNull PsiLambdaExpression myLambda;
    private final boolean myVoidMode;

    LambdaCodeBlockSurrounder(@NotNull PsiExpression expression, @NotNull PsiLambdaExpression lambda) {
      super(expression);
      myLambda = lambda;
      myVoidMode = PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(myLambda));
    }

    @Override
    public @NotNull ParentContext getExpectedParentContext() {
      PsiElement parent = myExpression.getParent();
      if (parent == myLambda) {
        return myVoidMode ? ParentContext.EXPRESSION : ParentContext.RETURN;
      }
      return super.getExpectedParentContext();
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      String replacementText = myVoidMode ? "{a;}" : "{return a;}";
      PsiCodeBlock newBody = factory.createCodeBlockFromText(replacementText, myLambda);
      LambdaUtil.extractSingleExpressionFromBody(newBody).replace(Objects.requireNonNull(myLambda.getBody()));
      newBody = (PsiCodeBlock)myLambda.getBody().replace(newBody);
      return newBody.getStatements()[0]; // either expression statement or return statement
    }
  }

  private static class YieldSurrounder extends CodeBlockSurrounder {
    private final PsiExpressionStatement myStatement;

    YieldSurrounder(PsiExpression expression, PsiExpressionStatement statement) {
      super(expression);
      myStatement = statement;
    }

    @Override
    public @NotNull ParentContext getExpectedParentContext() {
      return myExpression.getParent() == myStatement ? ParentContext.RETURN : super.getExpectedParentContext();
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      PsiBlockStatement block = (PsiBlockStatement)factory.createStatementFromText("{yield x;}", myStatement);
      PsiExpression newExpression = Objects.requireNonNull(((PsiYieldStatement)block.getCodeBlock().getStatements()[0]).getExpression());
      newExpression.replace(myStatement.getExpression());
      block = (PsiBlockStatement)myStatement.replace(block);
      return block.getCodeBlock().getStatements()[0];
    }
  }

  private static class SimpleSurrounder extends CodeBlockSurrounder {
    private final PsiStatement myStatement;

    SimpleSurrounder(PsiExpression expression, PsiStatement statement) {
      super(expression);
      myStatement = statement;
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      PsiBlockStatement block = (PsiBlockStatement)factory.createStatementFromText("{}", myStatement);
      block.getCodeBlock().add(myStatement);
      block = (PsiBlockStatement)myStatement.replace(block);
      return block.getCodeBlock().getStatements()[0];
    }
  }

  private static class ExtractFieldInitializerSurrounder extends CodeBlockSurrounder {
    private final PsiField myField;

    public ExtractFieldInitializerSurrounder(@NotNull PsiExpression expression, @NotNull PsiField field) {
      super(expression);
      myField = field;
    }

    @Override
    public @NotNull ParentContext getExpectedParentContext() {
      return myField.getInitializer() == myExpression ? ParentContext.ASSIGNMENT : super.getExpectedParentContext();
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      myField.normalizeDeclaration();
      PsiClassInitializer initializer =
        ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(myField), PsiClassInitializer.class);
      boolean isStatic = myField.hasModifierProperty(PsiModifier.STATIC);
      if (initializer == null || initializer.hasModifierProperty(PsiModifier.STATIC) != isStatic) {
        initializer = factory.createClassInitializer();
        if (isStatic) {
          Objects.requireNonNull(initializer.getModifierList()).setModifierProperty(PsiModifier.STATIC, true);
        }
        initializer = (PsiClassInitializer)myField.getParent().addAfter(initializer, myField);
      }
      PsiCodeBlock body = initializer.getBody();
      // There are at least two children: open and close brace
      // we will insert an initializer after the first brace and any whitespace which follow it
      PsiElement anchor = PsiTreeUtil.skipWhitespacesForward(body.getFirstChild());
      assert anchor != null;
      anchor = anchor.getPrevSibling();
      assert anchor != null;

      PsiExpressionStatement assignment =
        (PsiExpressionStatement)factory.createStatementFromText(myField.getName() + "=null;", initializer);
      assignment = (PsiExpressionStatement)body.addAfter(assignment, anchor);
      PsiExpression fieldInitializer = myField.getInitializer();
      fieldInitializer = ExpressionUtils.convertInitializerToExpression(fieldInitializer, factory, myField.getType());
      PsiExpression rExpression = ((PsiAssignmentExpression)assignment.getExpression()).getRExpression();
      assert fieldInitializer != null;
      assert rExpression != null;
      rExpression.replace(fieldInitializer);
      Objects.requireNonNull(myField.getInitializer()).delete();
      return assignment;
    }
  }

  private static class SplitTrySurrounder extends CodeBlockSurrounder {
    private final PsiResourceVariable myVariable;
    private final PsiTryStatement myStatement;

    SplitTrySurrounder(@NotNull PsiExpression expression,
                       @NotNull PsiResourceVariable variable,
                       @NotNull PsiTryStatement tryStatement) {
      super(expression);
      myVariable = variable;
      myStatement = tryStatement;
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      PsiResourceList list = Objects.requireNonNull(myStatement.getResourceList());
      PsiTryStatement copy = (PsiTryStatement)myStatement.copy();
      PsiResourceList copyList = copy.getResourceList();
      if (copyList == null) return myStatement;
      PsiCodeBlock tryBlock = myStatement.getTryBlock();
      if (tryBlock == null) return myStatement;
      List<PsiResourceListElement> elementsToMove = StreamEx.of(list.iterator()).dropWhile(e -> e != myVariable).toList();
      for (PsiResourceListElement element : elementsToMove) {
        element.delete();
      }
      for (PsiResourceListElement element : StreamEx.of(copyList.iterator())
        .limit(copyList.getResourceVariablesCount() - elementsToMove.size()).toList()) {
        element.delete();
      }
      PsiElement[] children = copyList.getChildren();
      if (children[0].textMatches("(") && children[1] instanceof PsiWhiteSpace) {
        children[1].delete();
      }
      for (PsiCatchSection section : copy.getCatchSections()) {
        section.delete();
      }
      PsiCodeBlock copyFinally = copy.getFinallyBlock();
      if (copyFinally != null) {
        PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsBackward(copyFinally);
        if (element != null && element.textMatches(PsiKeyword.FINALLY)) {
          element.delete();
        }
        copyFinally.delete();
      }
      PsiElement codeBlock = tryBlock.replace(factory.createCodeBlock());
      return (PsiStatement)codeBlock.add(copy);
    }
  }

  private static class WhileConditionSurrounder extends CodeBlockSurrounder {
    private final PsiWhileStatement myStatement;

    WhileConditionSurrounder(@NotNull PsiExpression expression, @NotNull PsiWhileStatement whileStatement) {
      super(expression);
      myStatement = whileStatement;
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      PsiWhileStatement whileStatement = myStatement;
      PsiExpression oldCondition = Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(whileStatement.getCondition()));
      PsiStatement body = whileStatement.getBody();
      PsiBlockStatement blockBody;
      int operandIndex = -1;
      if (oldCondition instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadic = (PsiPolyadicExpression)oldCondition;
        if (polyadic.getOperationTokenType().equals(JavaTokenType.ANDAND)) {
          PsiExpression[] operands = polyadic.getOperands();
          operandIndex = ContainerUtil.indexOf(Arrays.asList(operands), o -> PsiTreeUtil.isAncestor(o, myExpression, false));
        }
      }
      if (body == null) {
        PsiWhileStatement newWhileStatement = (PsiWhileStatement)factory.createStatementFromText("while(true) {}", whileStatement);
        Objects.requireNonNull(newWhileStatement.getCondition()).replace(oldCondition);
        whileStatement = (PsiWhileStatement)whileStatement.replace(newWhileStatement);
        blockBody = (PsiBlockStatement)Objects.requireNonNull(whileStatement.getBody());
        oldCondition = Objects.requireNonNull(whileStatement.getCondition());
      }
      else if (body instanceof PsiBlockStatement) {
        blockBody = (PsiBlockStatement)body;
      }
      else {
        PsiBlockStatement newBody = BlockUtils.createBlockStatement(project);
        newBody.getCodeBlock().add(body);
        blockBody = (PsiBlockStatement)body.replace(newBody);
      }
      PsiExpression lOperands;
      PsiExpression rOperands;
      if (operandIndex > 0) {
        PsiPolyadicExpression polyadic = (PsiPolyadicExpression)oldCondition;
        PsiExpression operand = polyadic.getOperands()[operandIndex];
        PsiJavaToken token = Objects.requireNonNull(polyadic.getTokenBeforeOperand(operand));
        lOperands = SplitConditionUtil.getLOperands(polyadic, token);
        rOperands = AndOrToIfSurrounder.getRightOperands(polyadic, operand);
      } else {
        lOperands = factory.createExpressionFromText("true", whileStatement);
        rOperands = oldCondition;
      }
      PsiCodeBlock codeBlock = blockBody.getCodeBlock();
      PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText("if(!true) break;", whileStatement);
      ifStatement = (PsiIfStatement)codeBlock.addAfter(ifStatement, codeBlock.getLBrace());
      PsiPrefixExpression negation = (PsiPrefixExpression)Objects.requireNonNull(ifStatement.getCondition());
      Objects.requireNonNull(negation.getOperand()).replace(rOperands);
      Objects.requireNonNull(whileStatement.getCondition()).replace(lOperands);
      return ifStatement;
    }
  }

  private static class TernaryToIfSurrounder extends CodeBlockSurrounder {
    private final PsiConditionalExpression myConditional;
    private final CodeBlockSurrounder myUpstream;

    public TernaryToIfSurrounder(@NotNull PsiExpression expression,
                                 @NotNull PsiConditionalExpression conditional,
                                 @NotNull CodeBlockSurrounder upstream) {
      super(expression);
      myConditional = conditional;
      myUpstream = upstream;
    }

    @Override
    public @NotNull ParentContext getExpectedParentContext() {
      if (myConditional.getThenExpression() == myExpression || myConditional.getElseExpression() == myExpression) {
        return myUpstream.getExpectedParentContext();
      }
      return super.getExpectedParentContext();
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      boolean then = PsiTreeUtil.isAncestor(myConditional.getThenExpression(), myExpression, false);
      SurroundResult upstreamResult = myUpstream.surround();
      PsiConditionalExpression ternary = Objects.requireNonNull(
        (PsiConditionalExpression)PsiUtil.skipParenthesizedExprDown(upstreamResult.getExpression()));
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ternary.getParent());
      PsiStatement statement = upstreamResult.getAnchor();
      if (parent instanceof PsiLocalVariable) {
        PsiLocalVariable variable = (PsiLocalVariable)parent;
        variable.normalizeDeclaration();
        PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
        PsiAssignmentExpression assignment = ExpressionUtils.splitDeclaration(declaration, project);
        if (assignment != null) {
          ternary = (PsiConditionalExpression)Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()));
          statement = (PsiStatement)assignment.getParent();
        }
      }
      CommentTracker ct = new CommentTracker();
      PsiIfStatement ifStatement =
        (PsiIfStatement)factory.createStatementFromText("if(" + ct.text(ternary.getCondition()) + ") {} else {}", statement);
      Object mark = new Object();
      PsiTreeUtil.mark(ternary, mark);
      for (PsiElement child : statement.getChildren()) {
        if (child instanceof PsiComment) {
          ct.delete(child);
        }
      }
      PsiStatement thenStatement = (PsiStatement)statement.copy();
      PsiConditionalExpression thenTernary = Objects.requireNonNull((PsiConditionalExpression)PsiTreeUtil.releaseMark(thenStatement, mark));
      PsiExpression thenBranch = ternary.getThenExpression();
      if (thenBranch != null) {
        thenTernary.replace(ct.markUnchanged(thenBranch));
      }
      PsiStatement elseStatement = (PsiStatement)statement.copy();
      PsiConditionalExpression elseTernary = Objects.requireNonNull((PsiConditionalExpression)PsiTreeUtil.releaseMark(elseStatement, mark));
      PsiExpression elseBranch = ternary.getElseExpression();
      if (elseBranch != null) {
        elseTernary.replace(ct.markUnchanged(elseBranch));
      }
      ifStatement = (PsiIfStatement)ct.replaceAndRestoreComments(statement, ifStatement);
      thenStatement =
        (PsiStatement)((PsiBlockStatement)Objects.requireNonNull(ifStatement.getThenBranch())).getCodeBlock().add(thenStatement);
      elseStatement =
        (PsiStatement)((PsiBlockStatement)Objects.requireNonNull(ifStatement.getElseBranch())).getCodeBlock().add(elseStatement);
      return then ? thenStatement : elseStatement;
    }
  }

  private static class AndOrToIfSurrounder extends CodeBlockSurrounder {
    private final @NotNull PsiPolyadicExpression myPolyadicExpression;
    private final @NotNull CodeBlockSurrounder myUpstream;

    AndOrToIfSurrounder(@NotNull PsiExpression expression,
                        @NotNull PsiPolyadicExpression polyadicExpression,
                        @NotNull CodeBlockSurrounder upstream) {
      super(expression);
      myPolyadicExpression = polyadicExpression;
      myUpstream = upstream;
    }

    @Override
    public @NotNull ParentContext getExpectedParentContext() {
      return myExpression.getParent() == myPolyadicExpression ? myUpstream.getExpectedParentContext() : super.getExpectedParentContext();
    }

    @Override
    @NotNull PsiStatement replace(@NotNull Project project, @NotNull PsiElementFactory factory) {
      PsiExpression[] operands = myPolyadicExpression.getOperands();
      int index = (int)StreamEx.of(operands).indexOf(o -> PsiTreeUtil.isAncestor(o, myExpression, false))
        .orElseThrow(IllegalStateException::new);
      SurroundResult upstreamResult = myUpstream.surround();
      PsiPolyadicExpression polyadicExpression =
        (PsiPolyadicExpression)Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(upstreamResult.getExpression()));
      PsiStatement statement = upstreamResult.getAnchor();
      PsiExpression operand = polyadicExpression.getOperands()[index];
      PsiExpression lOperands = SplitConditionUtil.getLOperands(polyadicExpression, Objects.requireNonNull(
        polyadicExpression.getTokenBeforeOperand(operand)));
      PsiExpression rOperands = getRightOperands(polyadicExpression, operand);
      if (statement instanceof PsiIfStatement) {
        return splitIf((PsiIfStatement)statement, polyadicExpression, lOperands, rOperands, project, factory);
      }
      assert statement instanceof PsiReturnStatement || statement instanceof PsiYieldStatement;
      return splitReturn(statement, polyadicExpression, lOperands, rOperands, project, factory);
    }

    @NotNull
    private static PsiStatement splitIf(@NotNull PsiIfStatement outerIf,
                                        @NotNull PsiPolyadicExpression andChain,
                                        @NotNull PsiExpression lOperands,
                                        @NotNull PsiExpression rOperands,
                                        @NotNull Project project,
                                        @NotNull PsiElementFactory factory) {
      PsiBlockStatement newThenBranch = (PsiBlockStatement)factory.createStatementFromText("{if(true);}", outerIf);
      PsiStatement thenBranch = Objects.requireNonNull(outerIf.getThenBranch());
      Objects.requireNonNull(((PsiIfStatement)newThenBranch.getCodeBlock().getStatements()[0]).getThenBranch()).replace(thenBranch);
      newThenBranch = (PsiBlockStatement)thenBranch.replace(newThenBranch);
      PsiIfStatement innerIf =
        (PsiIfStatement)CodeStyleManager.getInstance(project).reformat(newThenBranch.getCodeBlock().getStatements()[0]);
      Objects.requireNonNull(innerIf.getCondition()).replace(rOperands);
      andChain.replace(lOperands);
      return innerIf;
    }

    @NotNull
    private static PsiStatement splitReturn(@NotNull PsiStatement returnOrYieldStatement,
                                            @NotNull PsiPolyadicExpression condition,
                                            @NotNull PsiExpression lOperands,
                                            @NotNull PsiExpression rOperands,
                                            @NotNull Project project,
                                            @NotNull PsiElementFactory factory) {
      CommentTracker ct = new CommentTracker();
      boolean orChain = condition.getOperationTokenType().equals(JavaTokenType.OROR);
      String keyword = returnOrYieldStatement.getFirstChild().getText();
      String extractedCondition = orChain ? ct.text(lOperands) : BoolUtils.getNegatedExpressionText(lOperands, ct);
      String ifText = "if(" + extractedCondition + ") " + keyword + " " + orChain + ";";
      PsiStatement ifStatement = factory.createStatementFromText(ifText, returnOrYieldStatement);
      CodeStyleManager.getInstance(project).reformat(returnOrYieldStatement.getParent().addBefore(ifStatement, returnOrYieldStatement));
      ct.replaceAndRestoreComments(Objects.requireNonNull(condition), rOperands);
      return returnOrYieldStatement;
    }

    private static PsiExpression getRightOperands(PsiPolyadicExpression andChain, PsiExpression operand) {
      PsiExpression rOperands;
      if (operand == ArrayUtil.getLastElement(andChain.getOperands())) {
        rOperands = PsiUtil.skipParenthesizedExprDown(operand);
      }
      else {
        rOperands = SplitConditionUtil.getROperands(andChain, andChain.getTokenBeforeOperand(operand));
        // To preserve mark
        ((PsiPolyadicExpression)rOperands).getOperands()[0].replace(operand);
      }
      return rOperands;
    }
  }
}
