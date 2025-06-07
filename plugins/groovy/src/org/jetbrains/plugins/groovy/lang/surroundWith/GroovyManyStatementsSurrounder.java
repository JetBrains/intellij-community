// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public abstract class GroovyManyStatementsSurrounder implements Surrounder {

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    if (elements.length == 0) return false;

    for (PsiElement element : elements) {
      if (!isStatement(element)) return false;
    }

    if (elements[0] instanceof GrBlockStatement) {
      return false;
    }

    return true;
  }

  public static boolean isStatement(@NotNull PsiElement element) {
    return ";".equals(element.getText()) || element instanceof PsiComment || StringUtil.isEmptyOrSpaces(element.getText()) || PsiUtil.isExpressionStatement(element);
  }

  @Override
  public @Nullable TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
    if (elements.length == 0) return null;

    PsiElement element1 = elements[0];
    final GroovyPsiElement newStmt = doSurroundElements(elements, element1.getParent());
    assert newStmt != null;

    ASTNode parentNode = element1.getParent().getNode();
    if (elements.length > 1) {
      parentNode.removeRange(element1.getNode().getTreeNext(), elements[elements.length - 1].getNode().getTreeNext());
    }
    parentNode.replaceChild(element1.getNode(), newStmt.getNode());

    return getSurroundSelectionRange(newStmt);
  }

  protected static void addStatements(GrCodeBlock block, PsiElement[] elements) throws IncorrectOperationException {
    block.addRangeBefore(elements[0], elements[elements.length - 1], block.getRBrace());
  }

  protected abstract GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException;

  protected abstract TextRange getSurroundSelectionRange(GroovyPsiElement element);
}