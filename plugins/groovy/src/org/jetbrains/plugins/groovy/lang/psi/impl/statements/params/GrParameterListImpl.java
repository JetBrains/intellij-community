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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterListImpl extends GroovyPsiElementImpl implements GrParameterList {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl");
  public GrParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  public String toString() {
    return "Parameter list";
  }

  @NotNull
  public GrParameter[] getParameters() {
    return findChildrenByClass(GrParameter.class);
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public int getParametersCount() {
    return getParameters().length;
  }

  public void addParameterToEnd(GrParameter parameter) {
    GrParameter[] params = getParameters();
    if (params.length == 0) {
      add(parameter);
    }
    else {
      GrParameter last = params[params.length - 1];
      addAfter(parameter, last);
    }
  }

  public void addParameterToHead(GrParameter parameter) {
    GrParameter[] params = getParameters();
    if (params.length == 0) {
      add(parameter);
    }
    else {
      GrParameter first = params[0];
      addBefore(parameter, first);
    }
  }

  public int getParameterNumber(final GrParameter parameter) {
    for (int i = 0; i < getParameters().length; i++) {
      GrParameter param = getParameters()[i];
      if (param == parameter) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    GrParameter[] params = getParameters();

    if (params.length == 0) {
      add(element);
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
      add(element);
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
  }

}
