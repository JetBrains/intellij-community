/*
 * Copyright 2006-2013 Bas Leijdekkers
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
    if (BoolUtils.isTrue(condition)) {
      // no trickery needed
      replacementText.append("while(").append(condition.getText()).append(')');
      if (body != null) {
        replacementText.append(body.getText());
      }
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString());
      return;
    }
    final boolean noBraces = !(parent instanceof PsiCodeBlock);
    if (noBraces) {
      final PsiElement[] parentChildren = parent.getChildren();
      for (PsiElement child : parentChildren) {
        if (child == doWhileStatement) {
          break;
        }
        replacementText.append(child.getText());
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
            replacementText.append(child.getText());
          }
          else {
            parent.addBefore(child, doWhileStatement);
          }
        }
      }
    }
    else if (body != null) {
      if (noBraces) {
        replacementText.append(body.getText());
      }
      else {
        parent.addBefore(body, doWhileStatement);
      }
    }
    replacementText.append("while(");
    if (condition != null) {
      replacementText.append(condition.getText());
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
                  replacementText.append(variable.getName()).append(" = ").append(initializer.getText()).append(';');
                }
              }
            }
          }
          else {
            replacementText.append(child.getText());
          }
        }
      }
      replacementText.append('}');
    }
    else if (body != null) {
      replacementText.append(body.getText());
    }
    if (noBraces) {
      replacementText.append('}');
    }
    if (noBraces) {
      PsiReplacementUtil.replaceStatement((PsiStatement)parent, replacementText.toString());
    }
    else {
      PsiReplacementUtil.replaceStatement(doWhileStatement, replacementText.toString());
    }
  }
}
