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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

/**
 * @author ilyas
 */
public class GrListOrMapImpl extends GrExpressionImpl implements GrListOrMap {
  private static final TokenSet MAP_LITERAL_TOKEN_SET = TokenSet.create(GroovyElementTypes.ARGUMENT, GroovyTokenTypes.mCOLON);
  private static final Function<GrListOrMapImpl, PsiType> TYPES_CALCULATOR = new MyTypesCalculator();

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitListOrMap(this);
  }

  public String toString() {
    return "Generalized list";
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  private boolean isMapLiteral() {
    return findChildByType(MAP_LITERAL_TOKEN_SET) != null;
  }

  @Nullable
  public GrExpression[] getInitializers() {
    return findChildrenByClass(GrExpression.class);
  }

  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    return findChildrenByClass(GrNamedArgument.class);
  }

  private static class MyTypesCalculator implements Function<GrListOrMapImpl, PsiType> {
    public PsiType fun(GrListOrMapImpl listOrMap) {
      PsiManager manager = listOrMap.getManager();
      final GlobalSearchScope scope = listOrMap.getResolveScope();
      if (listOrMap.isMapLiteral()) {
        return manager.getElementFactory().createTypeByFQClassName("java.util.Map", scope);
      }

      PsiElement parent = listOrMap.getParent();
      if (parent.getParent() instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration) parent.getParent()).getTypeElementGroovy();
        if (typeElement != null) {
          PsiType declaredType = typeElement.getType();
          if (declaredType instanceof PsiArrayType) return declaredType;
        }
      }

      PsiClass listClass = manager.findClass("java.util.List", scope);
      if (listClass != null) {
        PsiTypeParameter[] typeParameters = listClass.getTypeParameters();
        if (typeParameters.length == 1) {
          GrExpression[] initializers = listOrMap.getInitializers();
          PsiType initializerType = getInitializerType(initializers);
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(typeParameters[0], initializerType);
          return manager.getElementFactory().createType(listClass, substitutor);
        } else {
          return manager.getElementFactory().createType(listClass);
        }
      }

      return null;
    }

    private PsiType getInitializerType(GrExpression[] initializers) {
      if (initializers.length == 0) return null;
      PsiManager manager = initializers[0].getManager();
      PsiType result = initializers[0].getType();
      for (int i = 1; i < initializers.length; i++) {
        final PsiType other = initializers[i].getType();
        if (other == null) continue;
        if (result == null) result = other;
        if (result.isAssignableFrom(other)) continue;
        if (other.isAssignableFrom(result)) result = other;
        result = GenericsUtil.getLeastUpperBound(result, other, manager);
      }

      return result;
    }
  }
}
