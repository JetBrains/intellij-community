/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyManyStatementsSurrounder implements Surrounder {
  public boolean isStatements(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof GrStatement)) {
        return false;
      }
    }
    return true;
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    if (elements.length == 0) return false;
    if (elements.length == 1) return elements[0] instanceof GrStatement && !(elements[0] instanceof GrBlockStatement);
    return isStatements(elements);
  }

  protected String getListElementsTemplateAsString(PsiElement... elements) {
    StringBuffer result = new StringBuffer();
    for (PsiElement element : elements) {
      result.append(element.getText());
      result.append("\n");
    }
    return result.toString();
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length == 0) return null;

    PsiElement element1 = elements[0];
    final GroovyPsiElement newStmt = doSurroundElements(elements);
    assert newStmt != null;

    ASTNode parentNode = element1.getParent().getNode();

    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (i == 0) {
        parentNode.replaceChild(element1.getNode(), newStmt.getNode());
      } else {
        if (parentNode != element.getParent().getNode()) return null;

        final int endOffset = element.getTextRange().getEndOffset();
        final PsiElement semicolon = PsiTreeUtil.findElementOfClassAtOffset(element.getContainingFile(), endOffset, PsiElement.class, false);
        if (semicolon != null && ";".equals(semicolon.getText())) {
          assert parentNode == semicolon.getParent().getNode();
          parentNode.removeChild(semicolon.getNode());
        }

        final PsiElement newLine = PsiTreeUtil.findElementOfClassAtOffset(element.getContainingFile(), endOffset, PsiElement.class, false);
        if (newLine != null && GroovyElementTypes.mNLS.equals(newLine.getNode().getElementType())) {
          assert parentNode == newLine.getParent().getNode();
          parentNode.removeChild(newLine.getNode());
        }

        parentNode.removeChild(element.getNode());
      }
    }

    return getSurroundSelectionRange(newStmt);
  }

  protected void addStatements(GrCodeBlock block, PsiElement[] elements) throws IncorrectOperationException {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      final GrStatement statement = (GrStatement) element;
      block.addStatementBefore(statement, null);
    }
  }

  protected abstract GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException;

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);
}