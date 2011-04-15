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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
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

      if (PsiImplUtil.isSimpleArrayAccess(thisType, argTypes, manager, resolveScope)) {
        return TypesUtil.boxPrimitiveType(((PsiArrayType)thisType).getComponentType(), manager, resolveScope);
      }

      GroovyResolveResult candidate = PsiImplUtil.getIndexPropertyMethodCandidate(thisType, argTypes, index);
      PsiType overloadedOperatorType = ResolveUtil.extractReturnTypeFromCandidate(candidate);

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

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }
}