// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;

/**
 * @author peter
 */
public abstract class AbstractClosureParameterEnhancer extends GrVariableEnhancer {
  @Override
  public final PsiType getVariableType(GrVariable variable) {
    if (!(variable instanceof GrParameter)) {
      return null;
    }

    GrClosableBlock closure;
    int paramIndex;

    if (variable instanceof ClosureSyntheticParameter) {
      closure = ((ClosureSyntheticParameter)variable).getClosure();
      paramIndex = 0;
    }
    else {
      PsiElement eParameterList = variable.getParent();
      if (!(eParameterList instanceof GrParameterList)) return null;

      PsiElement eClosure = eParameterList.getParent();
      if (!(eClosure instanceof GrClosableBlock)) return null;

      closure = (GrClosableBlock)eClosure;

      GrParameterList parameterList = (GrParameterList)eParameterList;
      paramIndex = parameterList.getParameterNumber((GrParameter)variable);
    }

    PsiType res = getClosureParameterType(closure, paramIndex);

    if (res instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)res).getBoxedType(closure);
    }

    return res;
  }

  @Nullable
  public static GrMethodCall findCall(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrMethodCall && ArrayUtil.contains(closure, ((GrMethodCall)parent).getClosureArguments())) {
      return (GrMethodCall)parent;
    }

    if (parent instanceof GrArgumentList) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrMethodCall) {
        return (GrMethodCall)pparent;
      }
    }

    return null;
  }

  @Nullable
  protected abstract PsiType getClosureParameterType(GrClosableBlock closure, int index);
}
