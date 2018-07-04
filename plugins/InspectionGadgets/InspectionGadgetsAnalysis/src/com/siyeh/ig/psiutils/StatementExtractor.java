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

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  public static PsiStatement[] generateStatements(List<PsiExpression> expressionsToKeep, PsiExpression root) {
    String statementsCode = generateStatementsText(expressionsToKeep, root);
    if(statementsCode.isEmpty()) return PsiStatement.EMPTY_ARRAY;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(root.getProject());
    PsiCodeBlock codeBlock = factory.createCodeBlockFromText("{" + statementsCode + "}", root);
    return codeBlock.getStatements();
  }

  public static String generateStatementsText(List<PsiExpression> expressionsToKeep, PsiExpression root) {
    Node result = StreamEx.ofReversed(expressionsToKeep).map(expression -> createNode(expression, root)).foldLeft(EMPTY, Node::prepend);
    return result.toString();
  }

  @NotNull
  private static Node createNode(@NotNull PsiExpression expression, @NotNull PsiExpression root) {
    Node result = new Expr(expression);
    PsiExpression parent;
    while (expression != root) {
      PsiElement parentElement = expression.getParent();
      if(parentElement instanceof PsiExpressionList) {
        parentElement = parentElement.getParent();
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
      return StreamEx.of(operands, 0, myLimit).map(invert ? condition1 -> BoolUtils.getNegatedExpressionText(condition1)
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
