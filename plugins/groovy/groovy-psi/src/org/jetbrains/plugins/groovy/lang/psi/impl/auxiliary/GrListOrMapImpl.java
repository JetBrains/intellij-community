/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrListOrMapImpl extends GrExpressionImpl implements GrListOrMap {
  private static final TokenSet MAP_LITERAL_TOKEN_SET = TokenSet.create(GroovyElementTypes.NAMED_ARGUMENT, GroovyTokenTypes.mCOLON);
  private static final Function<GrListOrMapImpl, PsiType> TYPES_CALCULATOR = new MyTypesCalculator();

  private final PsiReference myLiteralReference = new LiteralConstructorReference(this);
  private volatile GrExpression[] myInitializers = null;
  private volatile GrNamedArgument[] myNamedArguments = null;

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitListOrMap(this);
  }

  public String toString() {
    return "Generalized list";
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (getInitializers().length == 0) {
      return super.addInternal(first, last, getNode().getFirstChildNode(), false);
    }
    final ASTNode lastChild = getNode().getLastChildNode();
    getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", lastChild);
    return super.addInternal(first, last, lastChild.getTreePrev(), false);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi instanceof GrExpression || psi instanceof GrNamedArgument) {
      PsiElement prev = PsiUtil.getPrevNonSpace(psi);
      PsiElement next = PsiUtil.getNextNonSpace(psi);
      if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
        super.deleteChildInternal(prev.getNode());
      }
      else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
        super.deleteChildInternal(next.getNode());
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  @Override
  public boolean isMap() {
    return findChildByType(MAP_LITERAL_TOKEN_SET) != null;
  }

  @Override
  public boolean isEmpty() {
    return getInitializers().length == 0 && getNamedArguments().length == 0;
  }

  @Override
  public PsiElement getLBrack() {
    return findChildByType(GroovyTokenTypes.mLBRACK);
  }

  @Override
  public PsiElement getRBrack() {
    return findChildByType(GroovyTokenTypes.mRBRACK);
  }

  @Override
  @NotNull
  public GrExpression[] getInitializers() {
    GrExpression[] initializers = myInitializers;
    if (initializers == null) {
      initializers = PsiTreeUtil.getChildrenOfType(this, GrExpression.class);
      initializers = initializers == null ? GrExpression.EMPTY_ARRAY : initializers;
      myInitializers = initializers;
    }
    return initializers;
  }

  @Override
  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    GrNamedArgument[] namedArguments = myNamedArguments;
    if (namedArguments == null) {
      namedArguments = PsiTreeUtil.getChildrenOfType(this, GrNamedArgument.class);
      namedArguments = namedArguments == null ? GrNamedArgument.EMPTY_ARRAY : namedArguments;
      myNamedArguments = namedArguments;
    }
    return namedArguments;
  }

  @Override
  public GrNamedArgument findNamedArgument(@NotNull String label) {
    return PsiImplUtil.findNamedArgument(this, label);
  }

  @Override
  public PsiReference getReference() {
    return myLiteralReference;
  }

  @Override
  public void subtreeChanged() {
    myInitializers = null;
    myNamedArguments = null;
  }

  private static class MyTypesCalculator implements Function<GrListOrMapImpl, PsiType> {
    @Override
    @Nullable
    public PsiType fun(GrListOrMapImpl listOrMap) {
      if (listOrMap.isMap()) {
        return inferMapInitializerType(listOrMap);
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
    private static PsiClassType inferMapInitializerType(GrListOrMapImpl listOrMap) {
      GrNamedArgument[] namedArgs = listOrMap.getNamedArguments();

      if (namedArgs.length == 0) {
        PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond(listOrMap);

        if (lType instanceof PsiClassType && InheritanceUtil.isInheritor(lType, CommonClassNames.JAVA_UTIL_MAP)) {
          GlobalSearchScope scope = listOrMap.getResolveScope();
          JavaPsiFacade facade = JavaPsiFacade.getInstance(listOrMap.getProject());
          PsiClass hashMap = facade.findClass(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP, scope);
          if (hashMap != null) {
            PsiSubstitutor mapSubstitutor = PsiSubstitutor.EMPTY.
              put(hashMap.getTypeParameters()[0], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType,  CommonClassNames.JAVA_UTIL_MAP, 0, false)).
              put(hashMap.getTypeParameters()[1], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType,  CommonClassNames.JAVA_UTIL_MAP, 1, false));
            return facade.getElementFactory().createType(hashMap, mapSubstitutor);
          }
        }
      }

      return GrMapType.createFromNamedArgs(listOrMap, namedArgs);
    }


    private static PsiClassType getTupleType(final GrExpression[] initializers, GrListOrMap listOrMap) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(listOrMap.getProject());
      GlobalSearchScope scope = listOrMap.getResolveScope();

      if (initializers.length == 0) {
        PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond(listOrMap);

        if (lType instanceof PsiClassType && InheritanceUtil.isInheritor(lType, CommonClassNames.JAVA_UTIL_LIST)) {
          PsiClass arrayList = facade.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST, scope);
          if (arrayList == null) arrayList = facade.findClass(CommonClassNames.JAVA_UTIL_LIST, scope);
          if (arrayList != null) {
            PsiSubstitutor arrayListSubstitutor = PsiSubstitutor.EMPTY.
              put(arrayList.getTypeParameters()[0], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_LIST, 0, false));
            return facade.getElementFactory().createType(arrayList, arrayListSubstitutor);
          }
        }
      }

      return new GrTupleType(scope, facade) {
        @NotNull
        @Override
        protected PsiType[] inferComponents() {
          return ContainerUtil.map(initializers, new Function<GrExpression, PsiType>() {
            @Override
            public PsiType fun(final GrExpression expression) {
              return RecursionManager.doPreventingRecursion(expression, false, new Computable<PsiType>() {
                @Override
                public PsiType compute() {
                  return TypesUtil.boxPrimitiveType(expression.getType(), expression.getManager(), myScope);
                }
              });
            }
          }, new PsiType[initializers.length]);
        }

        @Override
        public boolean isValid() {
          for (GrExpression initializer : initializers) {
            if (!initializer.isValid()) return false;
          }
          return true;
        }
      };
    }
  }
}
