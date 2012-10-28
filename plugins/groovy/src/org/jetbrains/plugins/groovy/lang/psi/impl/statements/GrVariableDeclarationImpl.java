
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 */
public class GrVariableDeclarationImpl extends GrStubElementBase<EmptyStub> implements GrVariableDeclaration, StubBasedPsiElement<EmptyStub> {
  private static final Logger LOG = Logger.getInstance(GrVariableDeclarationImpl.class);

  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrVariableDeclarationImpl(EmptyStub stub) {
    super(stub, GroovyElementTypes.VARIABLE_DEFINITION);
  }

  @Override
  public PsiElement getParent() {
    return getDefinitionParent();
  }

  @Override
  public <T extends GrStatement> T replaceWithStatement(T statement) {
    return GroovyPsiElementImpl.replaceWithStatement(this, statement);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @NotNull
  public GrModifierList getModifierList() {
    return (GrModifierList)findNotNullChildByType(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement == null) return;
      getModifierList().setModifierProperty(GrModifier.DEF, true);
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

    GrReferenceAdjuster.shortenReferences(newTypeElement);
  }

  @Override
  public boolean isTuple() {
    return findChildByType(GroovyTokenTypes.mLPAREN) != null;
  }

  @Nullable
  @Override
  public GrExpression getTupleInitializer() {
    return findChildByClass(GrExpression.class);
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

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    if (isTuple()) return null;
    return findChildByClass(GrTypeElement.class);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  public String toString() {
    return "Variable definitions";
  }

  public GrMember[] getMembers() {
    return findChildrenByClass(GrMember.class);
  }

  @NotNull
  public GrVariable[] getVariables() {
    return getStubOrPsiChildren(GroovyElementTypes.VARIABLES, GrVariable.ARRAY_FACTORY);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null && lastParent == getTupleInitializer()) {
      return true;
    }

    for (final GrVariable variable : getVariables()) {
      if (lastParent == variable) break;
      if (lastParent instanceof GrMethod && !(variable instanceof GrField)) break;
      if (!ResolveUtil.processElement(processor, variable, state)) return false;
    }

    return true;
  }
}
