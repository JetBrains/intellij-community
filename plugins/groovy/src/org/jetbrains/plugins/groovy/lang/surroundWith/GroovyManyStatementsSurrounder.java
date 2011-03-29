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
package org.jetbrains.plugins.groovy.lang.surroundWith;

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
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyManyStatementsSurrounder implements Surrounder {

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    if (elements.length == 0) return false;

    for (PsiElement element : elements) {
      if (!isStatement(element)) return false;
    }

    if (elements[0] instanceof GrBlockStatement) {
      return false;
    }

    return true;
  }

  public static boolean isStatement(PsiElement element) {
    if (!(element instanceof GrStatement)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof GrStatementOwner)) {
      return false;
    }
    return true;
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

  protected static void addStatements(GrCodeBlock block, PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      final GrStatement statement = (GrStatement)element;
      block.addStatementBefore(statement, null);
    }
  }

  protected abstract GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException;

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);
}