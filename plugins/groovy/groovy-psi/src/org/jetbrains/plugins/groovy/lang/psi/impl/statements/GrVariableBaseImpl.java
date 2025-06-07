// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableStubBase;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public abstract class GrVariableBaseImpl<T extends GrVariableStubBase> extends GrStubElementBase<T> implements GrVariable, StubBasedPsiElement<T> {
  protected static final Logger LOG = Logger.getInstance(GrVariableBaseImpl.class);

  protected GrVariableBaseImpl(ASTNode node) {
    super(node);
  }

  protected GrVariableBaseImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public @Nullable PsiTypeElement getTypeElement() {
    return PsiImplUtil.getOrCreateTypeElement(getTypeElementGroovy());
  }

  @Override
  public @Nullable PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public @Nullable Object computeConstantValue() {
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    PsiElement prev = PsiUtil.getPrevNonSpace(this);
    PsiElement next = PsiUtil.getNextNonSpace(this);
    ASTNode parentNode = parent.getNode();
    assert parentNode != null;
    if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      parent.deleteChildRange(prev, getPrevSibling());
    }
    else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      parent.deleteChildRange(getNextSibling(), next);
    }
    super.delete();
    if (parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getVariables().length == 0) {
      parent.delete();
    }
  }


  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(property);
  }

  @Override
  public @NotNull PsiType getType() {
    PsiType type = getDeclaredType();
    return type != null ? type : TypesUtil.getJavaLangObject(this);
  }

  @Override
  public @Nullable GrTypeElement getTypeElementGroovy() {
    T stub = getStub();
    if (stub != null) {
      return stub.getTypeElement();
    }

    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return ((GrVariableDeclaration)parent).getTypeElementGroovyForVariable(this);
    }

    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public @Nullable PsiType getTypeGroovy() {
    GrTypeElement typeElement = getTypeElementGroovy();
    PsiType declaredType = null;
    if (typeElement != null) {
      declaredType = typeElement.getType();
      if (!(declaredType instanceof PsiClassType)) {
        return declaredType;
      }
    }

    PsiType initializerType = RecursionManager.doPreventingRecursion(this, true, this::getInitializerType);
    if (declaredType == null) {
      return initializerType;
    }
    if (initializerType instanceof PsiClassType && TypesUtil.isAssignableWithoutConversions(declaredType, initializerType)) {
      return initializerType;
    }
    return declaredType;
  }

  @Override
  public void setType(@Nullable PsiType type) {
    final GrVariableDeclaration variableDeclaration = getDeclaration();
    if (variableDeclaration == null) return;
    final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovyForVariable(this);

    if (type == null) {
      if (typeElement != null) {
        if (!variableDeclaration.isTuple() && variableDeclaration.getModifierList().getModifiers().length == 0) {
          variableDeclaration.getModifierList().setModifierProperty(GrModifier.DEF, true);
        }
        typeElement.delete();
      }
      return;
    }
    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrTypeElement newTypeElement;
    try {
      newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (typeElement == null) {
      newTypeElement = (GrTypeElement)getParent().addBefore(newTypeElement, this);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    PsiElement ident = findChildByType(TokenSets.PROPERTY_NAMES);
    assert ident != null : getText();
    return ident;
  }

  @Override
  public @Nullable GrExpression getInitializerGroovy() {
    return GroovyPsiElementImpl.findExpressionChild(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    final GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(this, GrVariableDeclarationOwner.class);
    if (owner != null) return new LocalSearchScope(owner);
    return super.getUseScope();
  }

  @Override
  public @NotNull String getName() {
    T stub = getGreenStub();
    if (stub != null) {
      String name = stub.getName();
      if (name != null) {
        return name;
      }
    }
    return PsiImplUtil.getName(this);
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  public @Nullable GrModifierList getModifierList() {
    final GrVariableDeclaration variableDeclaration = getDeclaration();
    if (variableDeclaration != null) return variableDeclaration.getModifierList();
    return null;
  }

  private @Nullable GrVariableDeclaration getDeclaration() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return (GrVariableDeclaration)parent;
    }
    return null;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi == getInitializerGroovy()) {
      deleteChildInternal(findNotNullChildByType(GroovyTokenTypes.mASSIGN).getNode());
    }
    super.deleteChildInternal(child);
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    if (getParent() instanceof GrVariableDeclaration && ((GrVariableDeclaration)getParent()).isTuple()) {
      throw new UnsupportedOperationException("don't invoke 'setInitializer()' for tuple declaration");
    }

    GrExpression oldInitializer = getInitializerGroovy();
    if (initializer == null) {
      if (oldInitializer != null) {
        oldInitializer.delete();
        PsiElement assign = findChildByType(GroovyTokenTypes.mASSIGN);
        if (assign != null) {
          assign.delete();
        }
      }
      return;
    }


    if (oldInitializer != null) {
      oldInitializer.replaceWithExpression(initializer, true);
    }
    else {
      getNode().addLeaf(TokenType.WHITE_SPACE, " ", null);
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", null);
      addAfter(initializer, getLastChild());
    }
  }
}
