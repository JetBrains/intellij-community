/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class ScopeUtils {

  private ScopeUtils() {}

  @Nullable
  public static PsiElement findTighterDeclarationLocation(@NotNull PsiElement sibling, @NotNull PsiVariable variable) {
    PsiElement prevSibling = sibling.getPrevSibling();
    while (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof PsiComment) {
      prevSibling = prevSibling.getPrevSibling();
    }
    if (prevSibling instanceof PsiDeclarationStatement) {
      if (prevSibling.equals(variable.getParent())) {
        return null;
      }
      return findTighterDeclarationLocation(prevSibling, variable);
    }
    return prevSibling;
  }

  @Nullable
  public static PsiElement getChildWhichContainsElement(@NotNull PsiElement ancestor, @NotNull PsiElement element) {
    PsiElement child = element;
    PsiElement parent = child.getParent();
    while (!parent.equals(ancestor)) {
      child = parent;
      parent = child.getParent();
      if (parent == null) {
        return null;
      }
    }
    return child;
  }

  @Nullable
  public static PsiElement getCommonParent(@NotNull List<? extends PsiElement> referenceElements) {
    PsiElement commonParent = null;
    for (PsiElement referenceElement : referenceElements) {
      final PsiElement parent = PsiTreeUtil.getParentOfType(referenceElement, PsiCodeBlock.class, PsiForStatement.class, PsiTryStatement.class);
      if (parent != null && commonParent != null) {
        if (!commonParent.equals(parent)) {
          commonParent = PsiTreeUtil.findCommonParent(commonParent, parent);
          commonParent = PsiTreeUtil.getNonStrictParentOfType(commonParent, PsiCodeBlock.class, PsiForStatement.class, PsiTryStatement.class);
        }
      }
      else {
        commonParent = parent;
      }
    }

    if (commonParent instanceof PsiTryStatement) {
      PsiElement referenceElement = referenceElements.get(0);
      if (!PsiTreeUtil.isAncestor(((PsiTryStatement)commonParent).getResourceList(), referenceElement, false)) {
        commonParent = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, PsiForStatement.class);
      }
    }

    // make common parent may only be for-statement if first reference is
    // the initialization of the for statement or the initialization is
    // empty.
    if (commonParent instanceof PsiForStatement) {
      final PsiForStatement forStatement = (PsiForStatement)commonParent;
      final PsiElement referenceElement = referenceElements.get(0);
      final PsiStatement initialization = forStatement.getInitialization();
      if (!(initialization instanceof PsiEmptyStatement)) {
        if (initialization instanceof PsiExpressionStatement) {
          final PsiExpressionStatement statement = (PsiExpressionStatement)initialization;
          final PsiExpression expression = statement.getExpression();
          if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
            final PsiExpression lExpression = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
            if (!lExpression.equals(referenceElement)) {
              commonParent = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class);
            }
          }
          else {
            commonParent = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class);
          }
        }
        else {
          commonParent = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class);
        }
      }
    }

    // common parent may not be a switch() statement to avoid narrowing
    // scope to inside switch branch
    if (commonParent != null) {
      final PsiElement parent = commonParent.getParent();
      if (parent instanceof PsiSwitchStatement) {
        if (referenceElements.size() > 1) {
          return PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false);
        }
        else if (PsiTreeUtil.getParentOfType(referenceElements.get(0), PsiSwitchLabelStatement.class, true, PsiCodeBlock.class) != null) {
          // reference is a switch label
          return PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false);
        }
      }
    }
    return commonParent;
  }

  @Nullable
  public static PsiElement moveOutOfLoopsAndClasses(@NotNull PsiElement scope, @NotNull PsiElement maxScope) {
    PsiElement result = maxScope;
    if (result instanceof PsiLoopStatement) {
      return result;
    }
    while (!result.equals(scope)) {
      final PsiElement element = getChildWhichContainsElement(result, scope);
      if (element instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)element;
        if (forStatement.getInitialization() instanceof PsiEmptyStatement) {
          return element;
        }
      }
      if (element == null || element instanceof PsiLoopStatement || element instanceof PsiClass || element instanceof PsiLambdaExpression) {
        while (result != null && !(result instanceof PsiCodeBlock)) {
          result = result.getParent();
        }
        return result;
      }
      else {
        result = element;
      }
    }
    return scope;
  }
}