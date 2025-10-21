// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiPatternVariableImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrInstanceofExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrEmptyLightModifierList;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.Objects;


public class GrPatternVariableImpl extends GroovyPsiElementImpl implements GrPatternVariable {
  private @Nullable GrModifierList modifierList = null;

  public GrPatternVariableImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GrTypeElement getTypeElementGroovy() {
    PsiElement parent = getParent();
    if (!(parent instanceof GrInstanceofExpressionImpl instanceofExpression)) {
      Logger.getInstance(GrPatternVariableImpl.class).error("Unexpected parent of pattern variable", new Attachment("File content", getContainingFile().getText()));
      return null;
    }
    return instanceofExpression.getTypeElement();
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitPatternVariable(this);
  }

  @Override
  public @Nullable GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public @Nullable PsiType getTypeGroovy() {
    GrTypeElement elementType = getTypeElementGroovy();
    return elementType != null ? elementType.getType() : null;
  }

  @Override
  public @NotNull GrModifierList getModifierList() {
    if (modifierList == null) {
      modifierList = new GrEmptyLightModifierList(this);
    }
    return modifierList;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  public @Nullable PsiElement getEllipsisDots() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null || typeElement == null) throw new IncorrectOperationException("Unable to change type of groovy pattern variable");

    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrTypeElement newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  public void setInitializerGroovy(@Nullable GrExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @NotNull PsiType getType() {
    PsiType typeGroovy = getTypeGroovy();
    return typeGroovy == null ? PsiType.getJavaLangObject(getManager(), getResolveScope()) : typeGroovy;
  }

  /**
   * This code mostly repeats logic from {@link com.intellij.psi.impl.source.tree.JavaSharedImplUtil#getPatternVariableDeclarationScope(PsiPatternVariable)}}.
   */
  @Override
  public @NotNull PsiElement getDeclarationScope() {
    PsiElement parent = getParent();
    if (!(parent instanceof GrInstanceOfExpression)) {
      return parent;
    }
    return findDeclarationScope(parent);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  private static PsiElement findDeclarationScope(@NotNull PsiElement parent) {
    for (PsiElement nextParent = parent.getParent(); ; parent = nextParent, nextParent = parent.getParent()) {
      if (nextParent instanceof GrParenthesizedExpression || nextParent instanceof GrTraditionalForClause) continue;
      if (nextParent instanceof GrLogicalExpression logicalExpression) {
        IElementType operationTokenType = logicalExpression.getOperationTokenType();
        if (operationTokenType.equals(GroovyElementTypes.T_LAND) ||
            operationTokenType.equals(GroovyElementTypes.T_LOR) ||
            operationTokenType.equals(GroovyElementTypes.T_IMPL)) continue;
      }
      if (nextParent instanceof GrForStatement ||
          nextParent instanceof GrConditionalExpression && parent == ((GrConditionalExpression)nextParent).getCondition()) {
        return nextParent;
      }
      if (nextParent instanceof GrUnaryExpression unaryExpression &&
          unaryExpression.getOperationTokenType().equals(GroovyElementTypes.T_NOT)) {
        continue;
      }
      if (nextParent instanceof GrIfStatement) {
        return nextParent.getParent();
      }
      if (nextParent instanceof GrLoopStatement) {
        return nextParent.getParent();
      }
      return parent;
    }
  }

  @Override
  public boolean isVarArgs() {
    return false;
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
  public @NotNull String getName() {
    PsiElement identifier = getNameIdentifierGroovy();
    return identifier.getText();
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @Override
  protected @Nullable Icon getElementIcon(int flags) {
    return JetgroovyIcons.Groovy.Variable;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (this != lastParent && !isUnnamed()) {
      return ResolveUtil.processElement(processor, this, state);
    }
    return true;
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    PsiElement ident = findChildByType(TokenSets.PROPERTY_NAMES);
    if (ident == null) {
      PsiFile file = getContainingFile();
      Logger.getInstance(PsiPatternVariableImpl.class).error("Pattern variable without identifier", new Attachment("File content", file.getText()));
    }
    return Objects.requireNonNull(ident);
  }
}
