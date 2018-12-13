/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StatementExtractor {
  private static final Node EMPTY = new Node(null) {
    @Override
    public Node prepend(Node node) {
      return node;
    }

    @Override
    public String toString() {
      return "";
    }
  };

  /**
   * Generate statements from subexpressions of root expression which must be kept.
   *
   * @param expressionsToKeep list of expressions to keep. Each expression must be a descendant of root, they must be ordered inside list
   *                          in program order and cannot be ancestors of each other.
   * @param root a root expression
   * @return an array of non-physical statements which represent the same logic as passed expressions
   */
  @NotNull
  public static PsiStatement[] generateStatements(List<? extends PsiExpression> expressionsToKeep, PsiExpression root) {
    String statementsCode = generateStatementsText(expressionsToKeep, root);
    if(statementsCode.isEmpty()) return PsiStatement.EMPTY_ARRAY;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(root.getProject());
    PsiCodeBlock codeBlock = factory.createCodeBlockFromText("{" + statementsCode + "}", root);
    return codeBlock.getStatements();
  }

  public static String generateStatementsText(List<? extends PsiExpression> expressionsToKeep, PsiExpression root) {
    Node result = StreamEx.ofReversed(expressionsToKeep).map(expression -> createNode(expression, root)).foldLeft(EMPTY, Node::prepend);
    return result.toString();
  }

  @NotNull
  private static Node createNode(@NotNull PsiExpression expression, @NotNull PsiExpression root) {
    Node result = new Expr(expression);
    PsiExpression parent;
    while (expression != root) {
      PsiElement parentElement = expression.getParent();
      if (parentElement instanceof PsiExpressionList) {
        parentElement = parentElement.getParent();
      }
      if (parentElement instanceof PsiStatement) {
        PsiSwitchExpression switchExpression =
          PsiTreeUtil.getParentOfType(parentElement, PsiSwitchExpression.class, true, PsiMember.class, PsiLambdaExpression.class);
        if (switchExpression != null && PsiTreeUtil.isAncestor(root, switchExpression, false)) {
          boolean isBreak =
            parentElement instanceof PsiBreakStatement && ((PsiBreakStatement)parentElement).findExitedElement() == switchExpression;
          boolean isRuleExpression =
            parentElement instanceof PsiExpressionStatement && parentElement.getParent() instanceof PsiSwitchLabeledRuleStatement &&
            ((PsiSwitchLabeledRuleStatement)parentElement.getParent()).getEnclosingSwitchBlock() == switchExpression;
          if (isBreak || isRuleExpression) {
            result = new Switch(switchExpression, Collections.singletonMap((PsiStatement)parentElement, result));
          }
          else {
            result = new Switch(switchExpression, Collections.emptyMap());
          }
          expression = switchExpression;
          continue;
        }
      }
      parent = ObjectUtils.tryCast(parentElement, PsiExpression.class);
      if (parent == null) {
        String message = PsiTreeUtil.isAncestor(root, expression, false) ?
                         "Expected to have expression parent" :
                         "Supplied root is not the expression ancestor";
        throw new RuntimeExceptionWithAttachments(message,
                                                  new Attachment("expression.txt", expression.getText()),
                                                  new Attachment("root.txt", root.getText()));
      }
      result = foldNode(result, expression, parent);
      expression = parent;
    }
    return result;
  }

  @NotNull
  private static Node foldNode(@NotNull Node node, @NotNull PsiExpression expression, @NotNull PsiExpression parent) {
    if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      IElementType type = polyadic.getOperationTokenType();
      boolean and;
      if (type == JavaTokenType.ANDAND) {
        and = true;
      }
      else if (type == JavaTokenType.OROR) {
        and = false;
      }
      else {
        return node;
      }
      PsiExpression[] operands = polyadic.getOperands();
      int index = ArrayUtil.indexOf(operands, expression);
      if (index == 0) return node;
      return new Cond(parent, parent, index, and ? node : EMPTY, and ? EMPTY : node);
    }
    if (parent instanceof PsiConditionalExpression) {
      PsiConditionalExpression ternary = (PsiConditionalExpression)parent;
      if (expression == ternary.getThenExpression()) {
        return new Cond(ternary, ternary.getCondition(), -1, node, EMPTY);
      }
      if (expression == ternary.getElseExpression()) {
        return new Cond(ternary, ternary.getCondition(), -1, EMPTY, node);
      }
    }
    return node;
  }

  private static abstract class Node {
    final PsiExpression myAnchor;

    protected Node(PsiExpression anchor) {
      myAnchor = anchor;
    }

    public abstract Node prepend(Node node);

    public abstract String toString();
  }

  private static class Cond extends Node {
    private final @NotNull PsiExpression myCondition;
    private final @NotNull Node myThenBranch;
    private final @NotNull Node myElseBranch;
    private final int myLimit;

    private Cond(@NotNull PsiExpression anchor,
                 @NotNull PsiExpression condition,
                 int limit,
                 @NotNull Node thenBranch,
                 @NotNull Node elseBranch) {
      super(anchor);
      myCondition = condition;
      myLimit = limit;
      assert limit < 0 || condition instanceof PsiPolyadicExpression;
      myThenBranch = thenBranch;
      myElseBranch = elseBranch;
    }

    private String getCondition(boolean invert) {
      if (myLimit < 0) {
        return invert ? BoolUtils.getNegatedExpressionText(myCondition) : myCondition.getText();
      }
      PsiPolyadicExpression condition = (PsiPolyadicExpression)myCondition;
      PsiExpression[] operands = condition.getOperands();
      String joiner = (condition.getOperationTokenType() == JavaTokenType.ANDAND) != invert ? "&&" : "||";
      return StreamEx.of(operands, 0, myLimit).map(invert ? BoolUtils::getNegatedExpressionText
                                                          : PsiExpression::getText)
        .joining(joiner);
    }

    @Override
    public String toString() {
      if (myThenBranch == EMPTY) {
        return "if(" + getCondition(true) + ") {" + myElseBranch + "}";
      }
      return "if(" + getCondition(false) + ") {" + myThenBranch + "}" + (myElseBranch == EMPTY ? "" : "else {" + myElseBranch + "}");
    }

    @Override
    public Node prepend(Node node) {
      PsiExpression thatAnchor = node.myAnchor;
      if(thatAnchor == null) return this;
      if(thatAnchor == myAnchor) {
        assert node instanceof Cond;
        Cond cond = (Cond)node;
        assert myCondition == cond.myCondition;
        if(myLimit == cond.myLimit) {
          return new Cond(myAnchor, myCondition, myLimit, myThenBranch.prepend(cond.myThenBranch), myElseBranch.prepend(cond.myElseBranch));
        }
        assert myLimit > cond.myLimit;
        return this;
      }
      if(PsiTreeUtil.isAncestor(myCondition, thatAnchor, false)) {
        return this;
      }
      return new Cons(node, this);
    }
  }

  private static class Expr extends Node {
    private Expr(@NotNull PsiExpression expression) {
      super(expression);
    }

    @Override
    public Node prepend(Node node) {
      return node.myAnchor == null ? this : new Cons(node, this);
    }

    public String toString() {
      return myAnchor.getText() + ";";
    }
  }

  private static class Switch extends Node {
    private static final Key<Node> NODE_KEY = Key.create("SwitchNode");

    private final @NotNull Map<PsiStatement, Node> myReturns;

    private Switch(@NotNull PsiSwitchExpression expression, @NotNull Map<PsiStatement, Node> sideEffectReturns) {
      super(expression);
      myReturns = sideEffectReturns;
    }

    @Override
    public Node prepend(Node node) {
      if (node.myAnchor == null) return this;
      if (node instanceof Switch && node.myAnchor == myAnchor) {
        if (myReturns.isEmpty()) return node;
        if (((Switch)node).myReturns.isEmpty()) return this;
        Map<PsiStatement, Node> newMap = new HashMap<>(myReturns);
        ((Switch)node).myReturns.forEach((statement, n) -> newMap.merge(statement, n, Node::prepend));
        return new Switch((PsiSwitchExpression)myAnchor, newMap);
      }
      return new Cons(node, this);
    }

    @Override
    public String toString() {
      myReturns.forEach((statement, node) -> statement.putCopyableUserData(NODE_KEY, node));
      PsiSwitchExpression copy = (PsiSwitchExpression)myAnchor.copy();
      Map<PsiStatement, PsiStatement[]> replacementMap = new HashMap<>();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myAnchor.getProject());
      PsiCodeBlock body = Objects.requireNonNull(copy.getBody());
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitExpressionStatement(PsiExpressionStatement statement) {
          if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement &&
              ((PsiSwitchLabeledRuleStatement)statement.getParent()).getEnclosingSwitchBlock() == copy) {
            process(statement);
          }
        }

        @Override
        public void visitBreakStatement(PsiBreakStatement statement) {
          if (statement.getValueExpression() != null && statement.findExitedElement() == copy) {
            process(statement);
          }
        }

        @Override
        public void visitExpression(PsiExpression expression) {
          // Do not go into any expressions
        }

        private void process(PsiStatement statement) {
          Node data = statement.getCopyableUserData(NODE_KEY);
          if (data == null) {
            replacementMap.put(statement, PsiStatement.EMPTY_ARRAY);
          }
          else {
            replacementMap.put(statement, factory.createCodeBlockFromText("{" + data + "}", statement).getStatements());
          }
        }
      });
      replacementMap.forEach((statement, replacements) -> {
        boolean keep = statement instanceof PsiBreakStatement && shouldKeepBreak(statement);
        if (!keep && replacements.length == 1) {
          statement.replace(replacements[0]);
        }
        else {
          if (!keep || replacements.length > 0) {
            if (!(statement.getParent() instanceof PsiCodeBlock)) {
              statement = BlockUtils.expandSingleStatementToBlockStatement(statement);
            }
            PsiElement parent = statement.getParent();
            for (PsiStatement replacement : replacements) {
              parent.addBefore(replacement, statement);
            }
          }
          if (keep) {
            Objects.requireNonNull(((PsiBreakStatement)statement).getValueExpression()).delete();
          }
          else {
            statement.delete();
          }
        }
      });
      return copy.getText();
    }

    public boolean shouldKeepBreak(PsiStatement statement) {
      if (PsiTreeUtil.skipWhitespacesAndCommentsForward(statement) instanceof PsiStatement) return true;
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiBlockStatement) {
          return shouldKeepBreak((PsiStatement)gParent);
        }
      }
      else if (parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement) {
        return shouldKeepBreak((PsiStatement)parent);
      }
      else if (parent instanceof PsiSwitchLabeledRuleStatement) {
        return false;
      }
      return true;
    }
  }

  private static class Cons extends Node {
    private final @NotNull Node myHead;
    private final @NotNull Node myTail;

    private Cons(@NotNull Node head, @NotNull Node tail) {
      super(head.myAnchor);
      assert !(head instanceof Cons);
      myHead = head;
      myTail = tail;
    }

    @Override
    public Node prepend(Node node) {
      if(node.myAnchor == null) return this;
      if(PsiTreeUtil.isAncestor(myHead.myAnchor, node.myAnchor, false)) {
        Node newHead = myHead.prepend(node);
        return new Cons(newHead, myTail);
      }
      return new Cons(node, this);
    }

    @Override
    public String toString() {
      return myHead.toString() + myTail;
    }
  }
}
