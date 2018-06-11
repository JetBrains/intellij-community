// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public abstract class GroovyPsiElementImpl extends ASTWrapperPsiElement implements GroovyPsiElement {

  public GroovyPsiElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    if (getParent() instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement) getParent()).deleteChildInternal(getNode());
    } else {
      getParent().deleteChildRange(this, this);
    }
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    acceptGroovyChildren(this, visitor);
  }

  @Nullable
  public static GrExpression findExpressionChild(final PsiElement element) {
    for (PsiElement cur = element.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) return (GrExpression)cur;
    }
    return null;
  }

  public static void acceptGroovyChildren(PsiElement parent, GroovyElementVisitor visitor) {
    PsiElement child = parent.getFirstChild();
    while (child != null) {
      ProgressManager.checkCanceled();
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  /**
   * don't remove. it is used by inheritors
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    removeElements(this, elements);
  }

  public static void removeElements(PsiElement from, PsiElement[] elements) {
    ASTNode parentNode = from.getNode();
    for (PsiElement element : elements) {
      if (element.isValid()) {
        ASTNode node = element.getNode();
        if (node == null || node.getTreeParent() != parentNode) {
          throw new IncorrectOperationException();
        }
        parentNode.removeChild(node);
      }
    }
  }

  /**
   * don't remove. it is used by inheritors
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public void removeStatement() throws IncorrectOperationException {
    removeStatement(this);
  }

  public static void removeStatement(GroovyPsiElement element) {
    if (element.getParent() == null ||
        element.getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = element.getParent().getNode();
    ASTNode prevNode = element.getNode().getTreePrev();
    parentNode.removeChild(element.getNode());
    if (prevNode != null && TokenSets.SEPARATORS.contains(prevNode.getElementType())) {
      parentNode.removeChild(prevNode);
    }
  }

  public <T extends GrStatement> T replaceWithStatement(@NotNull T newStmt) {
    return replaceWithStatement(this, newStmt);
  }

  public static <T extends GrStatement> T replaceWithStatement(GroovyPsiElement element, @NotNull T newStmt) {
    PsiElement parent = element.getParent();
    if (parent == null) {
      throw new PsiInvalidElementAccessException(element);
    }
    return (T)element.replace(newStmt);
  }


}

