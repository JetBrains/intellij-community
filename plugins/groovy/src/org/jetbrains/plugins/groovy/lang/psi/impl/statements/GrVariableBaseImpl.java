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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTupleDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyBaseElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;

/**
 * @author ilyas
 */
public abstract class GrVariableBaseImpl<T extends StubElement> extends GroovyBaseElementImpl<T> implements GrVariable {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl");

  public GrVariableBaseImpl(ASTNode node) {
    super(node);
  }

  protected GrVariableBaseImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
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

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    PsiElement prev = PsiUtil.getPrevNonSpace(this);
    PsiElement next = PsiUtil.getNextNonSpace(this);
    ASTNode parentNode = parent.getNode();
    assert parentNode != null;
    super.delete();
    if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      prev.delete();
    } else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      next.delete();
    }
    if (parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getVariables().length == 0) {
      parent.delete();
    }
  }


  //todo: see GrModifierListImpl.hasModifierProperty()
  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(property);
  }

  public String getElementToCompare() {
    return getName();
  }

  @NotNull
  public PsiType getType() {
    PsiType type = getDeclaredType();
    return type != null ? type : TypesUtil.getJavaLangObject(this);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    PsiElement parent = getParent();
    if (parent instanceof GrTupleDeclaration || !(parent instanceof GrVariableDeclaration)) {
      return null;
    }
    else {
      return ((GrVariableDeclaration)parent).getTypeElementGroovy();
    }
  }

  @Nullable
  public PsiType getDeclaredType() {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();

    return null;
  }

  @Nullable
  public PsiType getTypeGroovy() {
    GrExpression initializer = getInitializerGroovy();
    final PsiElement parent = getParent();

    if (parent instanceof GrTupleDeclaration && initializer != null){
      return initializer.getType();
    }

    GrTypeElement typeElement = getTypeElementGroovy();
    PsiType declaredType = null;
    if (typeElement != null) {
      declaredType = typeElement.getType();
      if (!(declaredType instanceof PsiClassType)) {
        return declaredType;
      }
    }

    if (initializer != null) {
      PsiType initializerType = initializer.getType(); // WARNING may give rise to SOE
      if (initializerType != null) {
        if (declaredType != null && initializerType instanceof PsiClassType) {
          final PsiClass declaredClass = ((PsiClassType)declaredType).resolve();
          if (declaredClass != null) {
            final PsiClassType.ClassResolveResult initializerResult = ((PsiClassType)initializerType).resolveGenerics();
            final PsiClass initializerClass = initializerResult.getElement();
            if (initializerClass != null &&
                !com.intellij.psi.util.PsiUtil.isRawSubstitutor(initializerClass, initializerResult.getSubstitutor())) {
              if (declaredClass == initializerClass) return initializerType;
              final PsiSubstitutor superSubstitutor =
                TypeConversionUtil.getClassSubstitutor(declaredClass, initializerClass, initializerResult.getSubstitutor());
              if (superSubstitutor != null) {
                return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(declaredClass, superSubstitutor);
              }
            }
          }
        }
      }

      if (declaredType == null) declaredType = initializerType;
    }


    return declaredType;
  }

  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement == null) return;
      final ASTNode typeElementNode = typeElement.getNode();
      final ASTNode parent = typeElementNode.getTreeParent();
      parent.addLeaf(GroovyTokenTypes.kDEF, GrModifier.DEF, typeElementNode);
      parent.removeChild(typeElementNode);
    } else {
      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      GrTypeElement newTypeElement;
      try {
        newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }

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

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement ident = findChildByType(TokenSets.PROPERTY_NAMES);
    assert ident != null : getText();
    return ident;
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    final PsiElement parent = getParent();
    if (parent instanceof GrTupleDeclaration){
      final GrTupleDeclaration tuple = (GrTupleDeclaration)parent;
      final GrExpression initializer = tuple.getInitializerGroovy();

      if (initializer instanceof GrListOrMap){
        final GrListOrMap listOrMap = (GrListOrMap)initializer;
        final GrExpression[] initializers = listOrMap.getInitializers();

        final int varNumber = tuple.getVariableNumber(this);
        if (initializers.length < varNumber + 1) return null;

        return initializers[varNumber];
      }
    }
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
  public SearchScope getUseScope() {
    final GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(this, GrVariableDeclarationOwner.class);
    if (owner != null) return new LocalSearchScope(owner);
    return super.getUseScope();
  }

  @NotNull
  public String getName() {
    return PsiImplUtil.getName(this);
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Nullable
  public GrModifierList getModifierList() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return ((GrVariableDeclaration)parent).getModifierList();
    } else if (parent instanceof GrTupleDeclaration) {
      return ((GrVariableDeclaration)parent.getParent()).getModifierList();
    }
    return null;
  }

  @Nullable
  public Icon getIcon(int flags) {
    return GroovyIcons.VARIABLE;
  }

  public PsiType getTypeNoResolve() {
    return getType();
  }

}
