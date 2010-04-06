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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;

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

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    final GrExpression[] initializers = getInitializers();
    if (initializers.length == 0) {
      return super.addInternal(first, last, getNode().getFirstChildNode(), false);
    }
    final ASTNode lastChild = getNode().getLastChildNode();
    getNode().addLeaf(mCOMMA, ",", lastChild);
    return super.addInternal(first, last, lastChild.getTreePrev(), false);
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  public boolean isMap() {
    return findChildByType(MAP_LITERAL_TOKEN_SET) != null;
  }

  @NotNull
  public GrExpression[] getInitializers() {
    return findChildrenByClass(GrExpression.class);
  }

  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    return findChildrenByClass(GrNamedArgument.class);
  }

  private static class MyTypesCalculator implements Function<GrListOrMapImpl, PsiType> {
    @Nullable
    public PsiType fun(GrListOrMapImpl listOrMap) {
      final GlobalSearchScope scope = listOrMap.getResolveScope();
      if (listOrMap.isMap()) {
        return inferMapInitializerType(listOrMap, JavaPsiFacade.getInstance(listOrMap.getProject()), scope);
      }

      PsiElement parent = listOrMap.getParent();
      if (parent.getParent() instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration)parent.getParent()).getTypeElementGroovy();
        if (typeElement != null) {
          PsiType declaredType = typeElement.getType();
          if (declaredType instanceof PsiArrayType) return declaredType;
        }
      }

      return getTupleType(listOrMap.getInitializers(), listOrMap);
    }

    @Nullable
    private static PsiClassType inferMapInitializerType(GrListOrMapImpl listOrMap, JavaPsiFacade facade, GlobalSearchScope scope) {
      PsiClass mapClass = facade.findClass("java.util.LinkedHashMap", scope);
      if (mapClass == null) {
        mapClass = facade.findClass(CommonClassNames.JAVA_UTIL_MAP, scope);
      }
      PsiElementFactory factory = facade.getElementFactory();
      if (mapClass != null) {
        PsiTypeParameter[] typeParameters = mapClass.getTypeParameters();
        if (typeParameters.length == 2) {
          GrNamedArgument[] namedArgs = listOrMap.getNamedArguments();
          GrExpression[] values = new GrExpression[namedArgs.length];
          GrArgumentLabel[] labels = new GrArgumentLabel[namedArgs.length];

          for (int i = 0; i < values.length; i++) {
            GrExpression expr = namedArgs[i].getExpression();
            if (expr == null) return null;
            values[i] = expr;
            GrArgumentLabel label = namedArgs[i].getLabel();
            if (label == null) return null;
            labels[i] = label;
          }

          PsiType initializerType = getInitializerType(values);
          PsiType labelType = getLabelsType(labels);
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.
            put(typeParameters[0], labelType).
            put(typeParameters[1], initializerType);
          return factory.createType(mapClass, substitutor);
        }
        else {
          return facade.getElementFactory().createType(mapClass);
        }
      }
      return null;
    }

    @Nullable
    private static PsiType getLabelsType(GrArgumentLabel[] labels) {
      if (labels.length == 0) return null;
      PsiType result = null;
      PsiManager manager = labels[0].getManager();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(labels[0].getProject());
      for (GrArgumentLabel label : labels) {
        PsiElement el = label.getNameElement();
        PsiType other;
        if (el instanceof GrParenthesizedExpression) {
          other = ((GrParenthesizedExpression)el).getType();
        }
        else {
          final ASTNode node = el.getNode();
          if (node != null) {
            other = TypesUtil.getPsiType(el, node.getElementType());
            if (other == null) {
              other = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, el.getResolveScope());
            }
          }
          else {
            other = null;
          }
        }
        result = getLeastUpperBound(result, other, manager);
      }
      return result;
    }

    private static PsiClassType getTupleType(GrExpression[] initializers, GrListOrMap listOrMap) {
      PsiType[] result = new PsiType[initializers.length];
      boolean isLValue = PsiUtil.isLValue(listOrMap);
      for (int i = 0; i < result.length; i++) {
        result[i] = isLValue ? initializers[i].getNominalType() : initializers[i].getType();
      }
      return new GrTupleType(result, JavaPsiFacade.getInstance(listOrMap.getProject()), listOrMap.getResolveScope());
    }

    @Nullable
    private static PsiType getInitializerType(GrExpression[] initializers) {
      if (initializers.length == 0) return null;
      PsiManager manager = initializers[0].getManager();
      PsiType result = initializers[0].getType();
      for (int i = 1; i < initializers.length; i++) {
        result = getLeastUpperBound(result, initializers[i].getType(), manager);
      }

      return result;
    }

    @Nullable
    private static PsiType getLeastUpperBound(PsiType result, PsiType other, PsiManager manager) {
      if (other == null) return result;
      if (result == null) result = other;
      if (result.isAssignableFrom(other)) return result;
      if (other.isAssignableFrom(result)) result = other;

      result = TypesUtil.getLeastUpperBound(result, other, manager);
      return result;
    }

  }
}
