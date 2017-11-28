/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isCompileStatic;

public class GrContainerConverter extends GrTypeConverter {
  @Override
  public boolean isAllowedInMethodCall() {
    return false;
  }

  @Nullable
  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (isCompileStatic(context)) return null;
    if (lType instanceof PsiArrayType) {
      PsiType lComponentType = ((PsiArrayType)lType).getComponentType();
      PsiType rComponentType = ClosureParameterEnhancer.findTypeForIteration(rType, context);
      if (rComponentType != null && TypesUtil.isAssignable(lComponentType, rComponentType, context)) {
        return Boolean.TRUE;
      }
    }

    return null;
  }
}
