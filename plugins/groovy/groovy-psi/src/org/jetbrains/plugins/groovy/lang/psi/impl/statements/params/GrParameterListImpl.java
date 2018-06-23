// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.stubs.EmptyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

/**
 * @author: Dmitry.Krasilschikov
 */
public class GrParameterListImpl extends GrStubElementBase<EmptyStub> implements GrParameterList, StubBasedPsiElement<EmptyStub> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl");

  public GrParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrParameterListImpl(EmptyStub stub) {
    super(stub, GroovyElementTypes.PARAMETERS_LIST);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  public String toString() {
    return "Parameter list";
  }

  @Override
  @NotNull
  public GrParameter[] getParameters() {
    return getStubOrPsiChildren(GroovyElementTypes.PARAMETER, GrParameter.ARRAY_FACTORY);
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  @Override
  public int getParametersCount() {
    return getParameters().length;
  }

  @Override
  public int getParameterNumber(final GrParameter parameter) {
    GrParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter param = parameters[i];
      if (param == parameter) {
        return i;
      }
    }
    return -1;
  }

  /*@Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    GrParameter[] params = getParameters();

    if (params.length == 0) {
      element = add(element);
    }
    else {
      element = super.addAfter(element, anchor);
      final ASTNode astNode = getNode();
      if (anchor != null) {
        astNode.addLeaf(mCOMMA, ",", element.getNode());
      }
      else {
        astNode.addLeaf(mCOMMA, ",", element.getNextSibling().getNode());
      }
      CodeStyleManager.getInstance(getManager().getProject()).reformat(this);
    }

    return element;
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    GrParameter[] params = getParameters();

    if (params.length == 0) {
      element = add(element);
    }
    else {
      element = super.addBefore(element, anchor);
      final ASTNode astNode = getNode();
      if (anchor != null) {
        astNode.addLeaf(mCOMMA, ",", anchor.getNode());
      }
      else {
        astNode.addLeaf(mCOMMA, ",", element.getNode());
      }
      CodeStyleManager.getInstance(getManager().getProject()).reformat(this);
    }

    return element;
  }*/


  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    GrParameter[] params = getParameters();

    ASTNode result = super.addInternal(first, last, anchor, before);
    if (first == last && first.getPsi() instanceof GrParameter && params.length > 0) {
      if (before.booleanValue() && anchor != null) {
        getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor);
      }
      else if (before.booleanValue() && anchor == null) {
        getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", result);
      }
      else if (!before.booleanValue() && anchor != null) {
        getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", result);
      }
      else if (!before.booleanValue() && anchor == null) {
        getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", result.getTreeNext());
      }
    }
    return result;
  }
}
