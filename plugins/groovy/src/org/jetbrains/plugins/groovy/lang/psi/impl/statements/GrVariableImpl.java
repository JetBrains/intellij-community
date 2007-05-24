/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.PomField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclarations;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 11.04.2007
 */
public class GrVariableImpl extends GroovyPsiElementImpl implements GrField {
  public GrVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Variable";
  }

  @NotNull
  public PsiType getType() {
    GrTypeElement typeElement = ((GrVariableDeclarations) getParent()).getTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();

    GrExpression initializer = getInitializerGroovy();
    if (initializer != null) {
      if (!(initializer instanceof GrReferenceExpression) || !initializer.getText().equals(getName())) { //prevent infinite recursion
        PsiType initializerType = initializer.getType();
        if (initializerType != null) return initializerType;
      }
    }

    return getManager().getElementFactory().createTypeByFQClassName("java.lang.Object", getResolveScope());
  }

  @Nullable
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Nullable
  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return false;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Nullable
  public Object computeConstantValue() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitField(this);
  }

  public String getName() {
    return getNameIdentifierGroovy().getText();
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    return findChildByClass(GrExpression.class);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyElementFactory.getInstance(getProject()).createIdentifierFromText(name).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);

    return this;
  }

  public PomField getPom() {
    return null;
  }

  public void setInitializer(@Nullable PsiExpression psiExpression) throws IncorrectOperationException {
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    PsiElement nameId = getNameIdentifierGroovy();
    return new JavaIdentifier(getManager(), getContainingFile(), nameId.getTextRange());
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent().getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      return (PsiClass) parent.getParent();
    }
    return null;
  }

  @Nullable
  public PsiModifierList getModifierList() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclarations) {
      return ((GrVariableDeclarations) parent).getModifierList();
    }
    return null;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    PsiModifierList modifierList = getModifierList();
    if (modifierList != null) return modifierList.hasModifierProperty(property);
    return false;
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }
}
