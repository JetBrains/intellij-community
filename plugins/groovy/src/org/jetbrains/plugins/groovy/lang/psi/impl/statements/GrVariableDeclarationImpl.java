/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public class GrVariableDeclarationImpl extends GroovyPsiElementImpl implements GrVariableDeclaration {
  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  public String toString() {
    return "Variable definitions";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrVariableImpl variable : findChildrenByClass(GrVariableImpl.class)) {
      if (lastParent != variable && !ResolveUtil.processElement(processor, variable)) return false;
    }

    return true;
  }

  public GrModifierList getModifierList() {
    return findChildByClass(GrModifierList.class);
  }

  public GrVariable[] getVariables() {
    return findChildrenByClass(GrVariable.class);
  }

  public void removeVariable(@NotNull GrVariable variable) throws IncorrectOperationException {

    final List<GrVariable> variables = Arrays.asList(getVariables());
    if (!variables.contains(variable)) {
      throw new IncorrectOperationException();
    }

    final ASTNode astNode = getNode();
    final ASTNode parent = astNode.getTreeParent();
    if (variables.size() == 1) {
      cleanAroundDeclarationBeforeRemove();
      parent.removeChild(astNode);
      return;
    }
    cleanAroundVariableBeforeRemove(variable);
    astNode.removeChild(variable.getNode());
    GroovyRefactoringUtil.reformatCode(this);

  }

  /**
   * Removes redundant nemlines and separators around declaration to be removed
   */
  private void cleanAroundDeclarationBeforeRemove() {
    final PsiElement next = PsiUtil.realNext(getNextSibling());
    if (next != null &&
        GroovyTokenTypes.mSEMI.equals(next.getNode().getElementType())) {
      next.getParent().getNode().removeChild(next.getNode());
      return;
    }
    final PsiElement previous = PsiUtil.realPrevious(getPrevSibling());
    if (previous != null &&
        GroovyTokenTypes.mSEMI.equals(previous.getNode().getElementType())) {
      previous.getParent().getNode().removeChild(previous.getNode());
    }
  }

  /**
   * Removes redundant colons around variable to be removed in case there are also
   * some variables
   *
   * @param variable
   */
  private void cleanAroundVariableBeforeRemove(GrVariable variable) {
    final PsiElement previous = PsiUtil.realPrevious(variable.getPrevSibling());
    if (previous != null &&
        GroovyTokenTypes.mCOMMA.equals(previous.getNode().getElementType())) {
      previous.getParent().getNode().removeChild(previous.getNode());
      return;
    }
    final PsiElement next = PsiUtil.realNext(variable.getNextSibling());
    if (next != null &&
        GroovyTokenTypes.mCOMMA.equals(next.getNode().getElementType())) {
      next.getParent().getNode().removeChild(next.getNode());
    }
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  public GrMember[] getMembers() {
    return findChildrenByClass(GrMember.class);
  }
}
