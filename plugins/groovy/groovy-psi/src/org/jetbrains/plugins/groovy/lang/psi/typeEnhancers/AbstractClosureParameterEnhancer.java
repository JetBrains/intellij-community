/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
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
  protected abstract PsiType getClosureParameterType(GrClosableBlock closure, int index);
}
