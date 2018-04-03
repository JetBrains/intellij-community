/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ipp.whileloop;

import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceDoWhileLoopWithWhileLoopIntention extends Intention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new DoWhileLoopPredicate();
  }

  protected void processIntention(@NotNull PsiElement element) {
    final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)element.getParent();
    if (doWhileStatement == null) {
      return;
    }
    final PsiStatement body = doWhileStatement.getBody();
    final PsiElement parent = doWhileStatement.getParent();
    final PsiExpression condition = doWhileStatement.getCondition();
    @NonNls final StringBuilder replacementText = new StringBuilder();
    CommentTracker commentTracker = new CommentTracker();
    if (BoolUtils.isTrue(condition)) {
      // no trickery needed
      replacementText.append("while(").append(commentTracker.text(condition)).append(')');
      if (body != null) {
        replacementText.append(commentTracker.text(body));
      }
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString(), commentTracker);
      return;
    }
    final boolean noBraces = !(parent instanceof PsiCodeBlock);
    if (noBraces) {
      final PsiElement[] parentChildren = parent.getChildren();
      for (PsiElement child : parentChildren) {
        if (child == doWhileStatement) {
          break;
        }
        replacementText.append(commentTracker.text(child));
      }
      replacementText.append('{');
    }
    if (body instanceof PsiBlockStatement) {
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        for (int i = 1, length = children.length - 1; i < length; i++) {
          final PsiElement child = children[i];
          if (child instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)child;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
              if (declaredElement instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)declaredElement;
                final PsiModifierList modifierList = variable.getModifierList();
                if (modifierList != null) {
                  modifierList.setModifierProperty(PsiModifier.FINAL, false);
                }
              }
            }
          }
          if (noBraces) {
            replacementText.append(commentTracker.text(child));
          }
          else {
            parent.addBefore(child, doWhileStatement);
          }
        }
      }
    }
    else if (body != null) {
      if (noBraces) {
        replacementText.append(commentTracker.text(body)).append("\n");
      }
      else {
        parent.addBefore(body, doWhileStatement);
      }
    }
    replacementText.append("while(");
    if (condition != null) {
      replacementText.append(commentTracker.text(condition));
    }
    replacementText.append(')');
    if (body instanceof PsiBlockStatement) {
      replacementText.append('{');
      final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        for (int i = 1; i < children.length - 1; i++) {
          final PsiElement child = children[i];
          if (child instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)child;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
              if (declaredElement instanceof PsiVariable) {
                // prevent duplicate variable declarations.
                final PsiVariable variable = (PsiVariable)declaredElement;
                final PsiExpression initializer = variable.getInitializer();
                if (initializer != null) {
                  replacementText.append(variable.getName()).append(" = ").append(commentTracker.text(initializer)).append(';');
                }
              }
            }
          }
          else {
            replacementText.append(commentTracker.text(child));
          }
        }
      }
      replacementText.append('}');
    }
    else if (body != null) {
      replacementText.append(commentTracker.text(body)).append("\n");
    }
    if (noBraces) {
      replacementText.append('}');
    }
    if (noBraces) {
      PsiReplacementUtil.replaceStatement((PsiStatement)parent, replacementText.toString(), commentTracker);
    }
    else {
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString(), commentTracker);
    }
  }
}
