// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableBaseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrParameterStub;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static java.util.Objects.requireNonNull;

public class GrParameterImpl extends GrVariableBaseImpl<GrParameterStub> implements GrParameter {

  public GrParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrParameterImpl(GrParameterStub stub) {
    super(stub, GroovyStubElementTypes.PARAMETER);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  @Override
  public String toString() {
    return "Parameter";
  }

  @Override
  public @Nullable PsiType getTypeGroovy() {
    final PsiType declaredType = getDeclaredType();
    if (declaredType != null) return declaredType;

    if (isVarArgs()) {
      PsiClassType type = TypesUtil.getJavaLangObject(this);
      return new PsiEllipsisType(type);
    }

    PsiElement parent = getParent();
    if (parent instanceof GrForInClause) {
      GrExpression iteratedExpression = ((GrForInClause)parent).getIteratedExpression();
      if (iteratedExpression == null) return null;
      PsiType result = ClosureParameterEnhancer.findTypeForIteration(iteratedExpression, this);
      if (result != null) {
        return result;
      }
    }
    else if (parent instanceof GrTraditionalForClause) {
      return super.getTypeGroovy();
    }
    else if (parent instanceof GrCatchClause) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, this);
    }

    return GrVariableEnhancer.getEnhancedType(this);
  }


  @Override
  public PsiType getDeclaredType() {
    PsiType type = super.getDeclaredType();
    if (isVarArgs()) {
      if (type == null) type = TypesUtil.getJavaLangObject(this);
      return new PsiEllipsisType(type);
    }
    return type;
  }

  @Override
  public @NotNull PsiType getType() {
    if (isMainMethodFirstUntypedParameter()) {
      return GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(CommonClassNames.JAVA_LANG_STRING + "[]", this).getType();
    }
    else {
      return super.getType();
    }
  }

  private boolean isMainMethodFirstUntypedParameter() {
    if (getTypeElementGroovy() != null) return false;
    if (!(getParent() instanceof GrParameterList parameterList)) return false;
    if (isOptional()) return false;

    if (!(parameterList.getParent() instanceof GrMethod method)) return false;

    return PsiImplUtil.isMainMethod(method);
  }

  @Override
  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement != null) typeElement.delete();
      return;
    }

    GrTypeElement newTypeElement;
    try {
      newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (typeElement == null) {
      final GrModifierList modifierList = getModifierList();
      newTypeElement = (GrTypeElement)addAfter(newTypeElement, modifierList);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  public boolean isOptional() {
    final GrParameterStub stub = getStub();
    if (stub != null) {
      return GrParameterStub.hasInitializer(stub.getFlags());
    }

    return getInitializerGroovy() != null;
  }

  @Override
  public @Nullable PsiElement getEllipsisDots() {
    return findChildByType(GroovyTokenTypes.mTRIPLE_DOT);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    if (!isPhysical()) {
      final PsiFile file = getContainingFile();
      final PsiElement context = file.getContext();
      if (context != null) return new LocalSearchScope(context);
      return super.getUseScope();
    }

    final PsiElement scope = getDeclarationScope();
    if (scope instanceof GrDocCommentOwner owner) {
      final GrDocComment comment = owner.getDocComment();
      if (comment != null) {
        return new LocalSearchScope(new PsiElement[]{scope, comment});
      }
    }

    return new LocalSearchScope(scope);
  }

  @Override
  public @NotNull GrModifierList getModifierList() {
    return getRequiredStubOrPsiChild(GroovyStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public @NotNull PsiElement getDeclarationScope() {
    return requireNonNull(getParentOfType(this, GrParametersOwner.class));
  }

  @Override
  public boolean isVarArgs() {
    GrParameterStub stub = getStub();
    if (stub != null) {
      return GrParameterStub.isVarRags(stub.getFlags());
    }

    PsiElement dots = getEllipsisDots();
    return dots != null;
  }
}
