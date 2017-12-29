// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableDeclarationStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;

/**
 * @author: Dmitry.Krasilschikov
 */
public class GrVariableDeclarationImpl extends GrStubElementBase<GrVariableDeclarationStub>
  implements GrVariableDeclaration, StubBasedPsiElement<GrVariableDeclarationStub> {

  private static final Logger LOG = Logger.getInstance(GrVariableDeclarationImpl.class);

  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableDeclarationImpl(@NotNull GrVariableDeclarationStub stub) {
    super(stub, GroovyElementTypes.VARIABLE_DEFINITION);
  }

  @Override
  public <T extends GrStatement> T replaceWithStatement(T statement) {
    return GroovyPsiElementImpl.replaceWithStatement(this, statement);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @Override
  @NotNull
  public GrModifierList getModifierList() {
    return getRequiredStubOrPsiChild(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement == null) return;
      if (getModifierList().getModifiers().length == 0) {
        getModifierList().setModifierProperty(GrModifier.DEF, true);
      }
      typeElement.delete();
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
      getModifierList().setModifierProperty(GrModifier.DEF, false);
      final GrVariable[] variables = getVariables();
      if (variables.length == 0) return;
      newTypeElement = (GrTypeElement)addBefore(newTypeElement, variables[0]);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  public boolean isTuple() {
    return findChildByType(GroovyTokenTypes.mLPAREN) != null;
  }

  @Nullable
  @Override
  public GrExpression getTupleInitializer() {
    return GroovyPsiElementImpl.findExpressionChild(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi == getTupleInitializer()) {
      deleteChildInternal(findNotNullChildByType(GroovyTokenTypes.mASSIGN).getNode());
    }
    super.deleteChildInternal(child);
  }

  @Override
  public GrTypeElement getTypeElementGroovyForVariable(GrVariable var) {
    if (isTuple()) {
      final PsiElement psiElement = PsiUtil.skipWhitespacesAndComments(var.getPrevSibling(), false);
      if (psiElement instanceof GrTypeElement) {
        return (GrTypeElement)psiElement;
      }
      return null;
    }
    else {
      return getTypeElementGroovy();
    }
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    GrVariableDeclarationStub stub = getStub();
    if (stub != null) {
      return stub.getTypeElement();
    }
    if (isTuple()) return null;
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  public String toString() {
    return "Variable definitions";
  }

  @Override
  public GrMember[] getMembers() {
    return findChildrenByClass(GrMember.class);
  }

  @Override
  @NotNull
  public GrVariable[] getVariables() {
    return getStubOrPsiChildren(TokenSets.VARIABLES, GrVariable.ARRAY_FACTORY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor)) return true;

    if (lastParent != null && !(getParent() instanceof GrTypeDefinitionBody) && lastParent == getTupleInitializer()) {
      return true;
    }

    for (final GrVariable variable : getVariables()) {
      if (lastParent == variable) break;
      if (lastParent instanceof GrMethod && !(variable instanceof GrField)) break;
      if (!ResolveUtil.processElement(processor, variable, state)) return false;
    }

    return true;
  }

  @Override
  public PsiReference getReference() {
    if (getTypeElementGroovy() != null) return null;
    TextRange range = getRangeForReference();
    if (range == null) return null;

    return new GrTypeReference(range);
  }

  private TextRange getRangeForReference() {
    PsiElement modifier = findSuitableModifier();
    if (modifier == null) return null;

    return modifier.getTextRange().shiftRight(-getTextRange().getStartOffset());
  }

  private PsiElement findSuitableModifier() {
    final GrModifierList list = getModifierList();

    PsiElement defModifier = list.getModifier(GrModifier.DEF);
    if (defModifier != null) return defModifier;

    PsiElement finalModifier = list.getModifier(PsiModifier.FINAL);
    if (finalModifier != null) return finalModifier;

    for (PsiElement element : list.getModifiers()) {
      if (!(element instanceof GrAnnotation)) {
        return element;
      }
    }

    return null;
  }

  private class GrTypeReference extends PsiReferenceBase<GrVariableDeclaration> {
    public GrTypeReference(TextRange range) {
      super(GrVariableDeclarationImpl.this, range, true);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      GrVariable[] variables = getVariables();
      if (variables.length == 0) return null;

      GrVariable resolved = variables[0];
      PsiType typeGroovy = resolved.getTypeGroovy();
      if (typeGroovy instanceof PsiClassType) {
        return ((PsiClassType)typeGroovy).resolve();
      }
      else {
        return resolved;
      }
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return EMPTY_ARRAY;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return getElement();
    }
  }
}
