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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static com.intellij.psi.util.PsiUtil.substituteTypeParameter;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

  private static final Function<GrIndexPropertyImpl, PsiType> TYPE_CALCULATOR = new NullableFunction<GrIndexPropertyImpl, PsiType>() {
    @Override
    public PsiType fun(GrIndexPropertyImpl index) {
      GrExpression selected = index.getSelectedExpression();
      PsiType thisType = selected.getType();

      if (thisType == null) return null;

      GrArgumentList argList = index.getArgumentList();
      if (argList == null) return null;

      PsiType[] argTypes = PsiUtil.getArgumentTypes(argList, false, null);

      final PsiManager manager = index.getManager();
      final GlobalSearchScope resolveScope = index.getResolveScope();

      if (argTypes.length == 0) {
        PsiType arrType = null;
        if (selected instanceof GrBuiltinTypeClassExpression) {
          arrType = ((GrBuiltinTypeClassExpression)selected).getPrimitiveType();
        }

        if (selected instanceof GrReferenceExpression) {
          final PsiElement resolved = ((GrReferenceExpression)selected).resolve();
          if (resolved instanceof PsiClass) {
            arrType = TypesUtil.createTypeByFQClassName(((PsiClass)resolved).getQualifiedName(), index);
          }
        }

        if (arrType != null) {
          final PsiArrayType param = arrType.createArrayType();
          return TypesUtil.createJavaLangClassType(param, index.getProject(), resolveScope);
        }
      }

      if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, manager, resolveScope)) {
        return TypesUtil.boxPrimitiveType(((PsiArrayType)thisType).getComponentType(), manager, resolveScope);
      }

      final GroovyResolveResult[] candidates = index.multiResolve(false);
      PsiType overloadedOperatorType = ResolveUtil.extractReturnTypeFromCandidate(PsiImplUtil.extractUniqueResult(candidates));

      PsiType componentType = extractMapValueType(thisType, argTypes, manager, resolveScope);

      if (overloadedOperatorType != null &&
          (componentType == null || !TypesUtil.isAssignable(overloadedOperatorType, componentType, manager, resolveScope))) {
        return TypesUtil.boxPrimitiveType(overloadedOperatorType, manager, resolveScope);
      }
      return componentType;
    }

    @Nullable
    private PsiType extractMapValueType(PsiType thisType, PsiType[] argTypes, PsiManager manager, GlobalSearchScope resolveScope) {
      if (argTypes.length != 1 || !InheritanceUtil.isInheritor(thisType, CommonClassNames.JAVA_UTIL_MAP)) return null;
      final PsiType substituted = substituteTypeParameter(thisType, CommonClassNames.JAVA_UTIL_MAP, 1, true);
      return TypesUtil.boxPrimitiveType(substituted, manager, resolveScope);
    }
  };
  private static final ResolveCache.PolyVariantResolver<GrIndexPropertyImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrIndexPropertyImpl>() {
      @Override
      public GroovyResolveResult[] resolve(GrIndexPropertyImpl index, boolean incompleteCode) {
        GrExpression selected = index.getSelectedExpression();
        PsiType thisType = selected.getType();

        if (thisType == null) return GroovyResolveResult.EMPTY_ARRAY;

        GrArgumentList argList = index.getArgumentList();
        if (argList == null) return GroovyResolveResult.EMPTY_ARRAY;

        PsiType[] argTypes = PsiUtil.getArgumentTypes(argList, false, null);

        final PsiManager manager = index.getManager();
        final GlobalSearchScope resolveScope = index.getResolveScope();

        if (argTypes.length == 0) {
          PsiType arrType = null;
          if (selected instanceof GrBuiltinTypeClassExpression) {
            arrType = ((GrBuiltinTypeClassExpression)selected).getPrimitiveType();
          }

          if (selected instanceof GrReferenceExpression) {
            final PsiElement resolved = ((GrReferenceExpression)selected).resolve();
            if (resolved instanceof PsiClass) {
              arrType = TypesUtil.createTypeByFQClassName(((PsiClass)resolved).getQualifiedName(), index);
            }
          }

          if (arrType != null) {
            return GroovyResolveResult.EMPTY_ARRAY;
          }
        }

        if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, manager, resolveScope)) {
          return GroovyResolveResult.EMPTY_ARRAY;
        }

        GroovyResolveResult[] candidates;
        final String name;
        if (PsiUtil.isLValue(index)) {
          name = "putAt";
          argTypes = ArrayUtil.append(argTypes, TypeInferenceHelper.getInitializerFor(index), PsiType.class);
        }
        else {
          name = "getAt";
        }
        candidates = ResolveUtil.getMethodCandidates(thisType, name, index, argTypes);

        //hack for remove DefaultGroovyMethods.getAt(Object, ...)
        if (candidates.length == 2) {
          for (int i = 0; i < candidates.length; i++) {
            GroovyResolveResult candidate = candidates[i];
            final PsiElement element = candidate.getElement();
            if (element instanceof GrGdkMethod) {
              final PsiMethod staticMethod = ((GrGdkMethod)element).getStaticMethod();
              final PsiParameter param = staticMethod.getParameterList().getParameters()[0];
              if (param.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                return new GroovyResolveResult[]{candidates[1 - i]};
              }
            }
          }
        }

        if (candidates.length != 1) {
          final GrTupleType tupleType = new GrTupleType(argTypes, JavaPsiFacade.getInstance(index.getProject()), index.getResolveScope());
          candidates = ResolveUtil.getMethodCandidates(thisType, name, index, tupleType);
        }
        return candidates;
      }
    };

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIndexProperty(this);
  }

  public String toString() {
    return "Property by index";
  }

  @NotNull
  public GrExpression getSelectedExpression() {
    GrExpression result = findChildByClass(GrExpression.class);
    assert result != null;
    return result;
  }

  @Nullable
  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return (GroovyResolveResult[])getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final int offset = findChildByType(GroovyTokenTypes.mLBRACK).getStartOffsetInParent();
    return new TextRange(offset, offset + 1);
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "Array-style access";
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}