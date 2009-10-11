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

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author ilyas
 */
public class PsiExpressionCategory implements PsiEnhancerCategory{

  @Nullable
  public static PsiClass getClassType(GrExpression expr) {
    final PsiType type = expr.getType();
    return PsiCategoryUtil.getClassType(type, expr);
  }

  /**
   * Returns arguments of a call expression
   * @param call
   * @return
   */
  public static Collection<GrExpression> getArguments(GrExpression call) {
    if (call instanceof GrMethodCallExpression) {
      return Arrays.asList(((GrMethodCallExpression)call).getExpressionArguments());
    }
    return new ArrayList<GrExpression>();
  }

}
