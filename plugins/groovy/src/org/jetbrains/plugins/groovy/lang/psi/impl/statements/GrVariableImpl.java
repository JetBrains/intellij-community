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
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.JavaIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 11.04.2007
 */
public class GrVariableImpl extends GroovyPsiElementImpl implements GrVariable {
  public GrVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariable(this);
  }

  public String toString() {
    return "Variable";
  }

  @NotNull
  public PsiType getType() {
    PsiType type = getDeclaredType();
    return type != null ? type : getManager().getElementFactory().createTypeByFQClassName("java.lang.Object", getResolveScope());
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return ((GrVariableDeclaration) getParent()).getTypeElementGroovy();
  }

  @Nullable
  public PsiType getDeclaredType() {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();

    return null;
  }

  @Nullable
  public PsiType getTypeGroovy(boolean inferFromInitializer) {
    GrTypeElement typeElement = getTypeElementGroovy();
    PsiType declaredType = null;
    if (typeElement != null) {
      declaredType = typeElement.getType();
      if (!(declaredType instanceof PsiClassType)) {
        return declaredType;
      }
    }

    GrExpression initializer = getInitializerGroovy();
    if (initializer != null) {
      PsiType initializerType = initializer.getType();
      if (initializerType != null) {
        if (declaredType != null && initializerType instanceof PsiClassType) {
          final PsiClass declaredClass = ((PsiClassType) declaredType).resolve();
          if (declaredClass != null) {
            final PsiClassType.ClassResolveResult initializerResult = ((PsiClassType) initializerType).resolveGenerics();
            final PsiClass initializerClass = initializerResult.getElement();
            if (initializerClass != null) {
              final PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(declaredClass, initializerClass, initializerResult.getSubstitutor());
              if (superSubstitutor != null) {
                return getManager().getElementFactory().createType(declaredClass, superSubstitutor);
              }
            }
          }
        }
      }

      if (inferFromInitializer && declaredType == null) declaredType = initializerType;
    }


    return declaredType;
  }

  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement == null) return;
      final ASTNode typeElementNode = typeElement.getNode();
      final ASTNode parent = typeElementNode.getTreeParent();
      parent.addLeaf(GroovyTokenTypes.kDEF, "def", typeElementNode);
      parent.removeChild(typeElementNode);
    } else {
      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      GrTypeElement newTypeElement = GroovyElementFactory.getInstance(getProject()).createTypeElement(type);
      final ASTNode newTypeElementNode = newTypeElement.getNode();
      if (typeElement == null) {
        final PsiElement defKeyword = findChildByType(GroovyTokenTypes.kDEF);
        if (defKeyword != null) {
          final ASTNode defKeywordNode = defKeyword.getNode();
          assert defKeywordNode != null;
          defKeywordNode.getTreeParent().removeChild(defKeywordNode);
        }
        final PsiElement nameID = getNameIdentifierGroovy();
        final ASTNode nameIdNode = nameID.getNode();
        assert nameIdNode != null;
        nameIdNode.getTreeParent().addChild(newTypeElementNode);
      } else {
        final ASTNode typeElementNode = typeElement.getNode();
        final ASTNode parent = typeElementNode.getTreeParent();
        parent.replaceChild(typeElementNode, newTypeElementNode);
      }

      PsiUtil.shortenReferences(newTypeElement);
    }
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

  @NotNull
  public SearchScope getUseScope() {
    final GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(this, GrVariableDeclarationOwner.class);
    if (owner != null) return new LocalSearchScope(owner);
    return super.getUseScope();
  }

  public String getName() {
    return getNameIdentifierGroovy().getText();
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement ident = findChildByType(GroovyTokenTypes.mIDENT);
    assert ident != null;
    return ident;
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    return findChildByClass(GrExpression.class);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    PsiElement nameId = getNameIdentifierGroovy();
    return new JavaIdentifier(getManager(), getContainingFile(), nameId.getTextRange());
  }

  @Nullable
  public GrModifierList getModifierList() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return ((GrVariableDeclaration) parent).getModifierList();
    }
    return null;
  }

  //todo: see GrModifierListImpl.hasModifierProperty()
  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(property);
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public String getElementToCompare() {
    return getName();
  }
}
