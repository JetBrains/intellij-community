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
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterImpl extends GrVariableImpl implements GrParameter {
  public GrParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "Parameter";
  }

  @Nullable
  public PsiType getTypeGroovy() {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (typeElement != null) {
      PsiType type = typeElement.getType();
      if (!isVarArgs()) {
        return type;
      }
      else {
        return new PsiEllipsisType(type);
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (isVarArgs()) {
      PsiClassType type = factory.createTypeByFQClassName("java.lang.Object", getResolveScope());
      return new PsiEllipsisType(type);
    }
    PsiElement parent = getParent();
    if (parent instanceof GrForInClause) {
      GrExpression iteratedExpression = ((GrForInClause)parent).getIteratedExpression();
      if (iteratedExpression instanceof GrRangeExpression) {
        return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_INTEGER, getResolveScope());
      }
      else if (iteratedExpression != null) {
        PsiType result = ClosureParameterEnhancer.findTypeForIteration(iteratedExpression, factory, this);
        if (result != null) {
          return result;
        }
      }
    } else if (parent instanceof GrCatchClause) {
      return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, getResolveScope());
    }

    return GrVariableEnhancer.getEnhancedType(this);
  }


  @Override
  public PsiType getDeclaredType() {
    final PsiType type = super.getDeclaredType();
    if (type != null && isVarArgs()) {
      return new PsiEllipsisType(type);
    }
    return type;
  }

  @NotNull
  public PsiType getType() {
    PsiType type = super.getType();
    if (isMainMethodFirstUntypedParameter()) {
      PsiClassType stringType =
        JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName("java.lang.String", getResolveScope());
      return stringType.createArrayType();
    }
    if (getParent() instanceof GrForClause) { //inside for loop
      final PsiType typeGroovy = getTypeGroovy();
      if (typeGroovy != null) return typeGroovy;
    }
    return type;
  }

  private boolean isMainMethodFirstUntypedParameter() {
    if (getTypeElementGroovy() != null) return false;

    if (getParent() instanceof GrParameterList) {
      GrParameterList parameterList = (GrParameterList)getParent();
      GrParameter[] params = parameterList.getParameters();
      if (params.length != 1 || this != params[0]) return false;

      if (parameterList.getParent() instanceof GrMethod) {
        GrMethod method = (GrMethod)parameterList.getParent();
        return PsiImplUtil.isMainMethod(method);
      }
    }
    return false;
  }

  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("NIY");
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  @Nullable
  public GrExpression getDefaultInitializer() {
    return findChildByClass(GrExpression.class);
  }

  public boolean isOptional() {
    return getDefaultInitializer() != null;
  }

  @NotNull
  public SearchScope getUseScope() {
    if (!isPhysical()) {
      final PsiFile file = getContainingFile();
      final PsiElement context = file.getContext();
      if (context != null) return new LocalSearchScope(context);
      return super.getUseScope();
    }

    final PsiElement scope = getDeclarationScope();
    if (scope instanceof GrDocCommentOwner) {
      GrDocCommentOwner owner = (GrDocCommentOwner)scope;
      final GrDocComment comment = owner.getDocComment();
      if (comment != null) {
        return new LocalSearchScope(new PsiElement[]{scope, comment});
      }
    }

    return new LocalSearchScope(scope);
  }

  @NotNull
  public String getName() {
    return getNameIdentifierGroovy().getText();
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @NotNull
  public GrModifierList getModifierList() {
    return findNotNullChildByClass(GrModifierList.class);
  }

  @NotNull
  public PsiElement getDeclarationScope() {
    final GrParametersOwner owner = PsiTreeUtil.getParentOfType(this, GrParametersOwner.class);
    assert owner != null;
    if (owner instanceof GrForInClause) return owner.getParent();
    return owner;
  }

  public boolean isVarArgs() {
    PsiElement dots = findChildByType(GroovyTokenTypes.mTRIPLE_DOT);
    return dots != null;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

}
