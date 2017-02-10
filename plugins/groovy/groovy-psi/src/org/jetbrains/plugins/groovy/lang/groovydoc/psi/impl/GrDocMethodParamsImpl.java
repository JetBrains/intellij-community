/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrDocMethodParamsImpl extends GroovyDocPsiElementImpl implements GrDocMethodParams {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocMethodParamsImpl");

  public GrDocMethodParamsImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "GrDocMethodParameterList";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocMethodParameterList(this);
  }

  @Override
  public PsiType[] getParameterTypes() {
    ArrayList<PsiType> types = new ArrayList<>();
    PsiManagerEx manager = getManager();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    for (GrDocMethodParameter parameter : getParameters()) {
      GrDocReferenceElement typeElement = parameter.getTypeElement();
      try {
        PsiType type = factory.createTypeFromText(typeElement.getText(), this);
        type = TypesUtil.boxPrimitiveType(type, manager, scope);
        types.add(type);
      } catch (IncorrectOperationException e) {
        LOG.info(e);
        types.add(null);
      }
    }
    return types.toArray(PsiType.createArray(types.size()));
  }

  @Override
  public GrDocMethodParameter[] getParameters() {
    List<GrDocMethodParameter> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (GrDocMethodParameter.class.isInstance(cur)) result.add((GrDocMethodParameter)cur);
    }
    return result.toArray(new GrDocMethodParameter[result.size()]);
  }

  @Override
  @NotNull
  public PsiElement getLeftParen() {
    ASTNode paren = getNode().findChildByType(GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN);
    assert paren != null;
    return paren.getPsi();
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    ASTNode paren = getNode().findChildByType(GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN);
    return paren != null ? paren.getPsi() : null;
  }

}
