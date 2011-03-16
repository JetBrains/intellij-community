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
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static com.intellij.psi.util.PsiUtil.substituteTypeParameter;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getSmartReturnType;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

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
    GrExpression selected = getSelectedExpression();
    PsiType thisType = selected.getType();

    if (thisType == null) return null;

    GrArgumentList argList = getArgumentList();
    if (argList == null) return null;

    PsiType[] argTypes = getArgumentTypes(argList);

    if (isSimpleArrayAccess(thisType, argTypes)) {
      return TypesUtil.boxPrimitiveType(((PsiArrayType)thisType).getComponentType(), getManager(), getResolveScope());
    }

    if (isSimpleTupleAccess(thisType, argTypes)) {
      PsiType[] types = ((GrTupleType)thisType).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    PsiType overloadedOperatorType = null;
    GroovyResolveResult[] candidates = ResolveUtil.getMethodCandidates(thisType, "getAt", this, argTypes);
    if (candidates.length != 1) {
      final GrTupleType tupleType = new GrTupleType(argTypes, JavaPsiFacade.getInstance(getProject()), getResolveScope());
      candidates = ResolveUtil.getMethodCandidates(thisType, "getAt", this, tupleType);
    }
    if (candidates.length == 1) {
      final PsiElement element = candidates[0].getElement();
      if (element instanceof PsiMethod) {
        overloadedOperatorType = candidates[0].getSubstitutor().substitute(getSmartReturnType((PsiMethod)element));
      }
    }

    PsiType componentType = extractMapValueType(thisType, argTypes);

    if (overloadedOperatorType != null &&
        (componentType == null || !TypesUtil.isAssignable(overloadedOperatorType, componentType, getManager(), getResolveScope()))) {
      return TypesUtil.boxPrimitiveType(overloadedOperatorType, getManager(), getResolveScope());
    }
    return componentType;
  }

  @Nullable
  private PsiType extractMapValueType(PsiType thisType, PsiType[] argTypes) {
    if (argTypes.length != 1 || !InheritanceUtil.isInheritor(thisType, CommonClassNames.JAVA_UTIL_MAP)) return null;
    final PsiType substituted = substituteTypeParameter(thisType, CommonClassNames.JAVA_UTIL_MAP, 1, true);
    return TypesUtil.boxPrimitiveType(substituted, getManager(), getResolveScope());
  }

  private boolean isSimpleTupleAccess(PsiType thisType, PsiType[] argTypes) {
    return thisType instanceof GrTupleType &&
           argTypes.length == 1 &&
           TypesUtil.isAssignable(PsiType.INT, argTypes[0], getManager(), getResolveScope());
  }

  private boolean isSimpleArrayAccess(PsiType thisType, PsiType[] argTypes) {
    return thisType instanceof PsiArrayType &&
           argTypes.length == 1 &&
           TypesUtil.isAssignable(PsiType.INT, argTypes[0], getManager(), getResolveScope());
  }

  private static PsiType[] getArgumentTypes(GrArgumentList argList) {
    GrExpression[] arguments = argList.getExpressionArguments();
    PsiType[] argTypes = new PsiType[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      PsiType argType = arguments[i].getType();
      if (argType == null) argType = TypesUtil.getJavaLangObject(argList);
      argTypes[i] = argType;
    }
    return argTypes;
  }
}