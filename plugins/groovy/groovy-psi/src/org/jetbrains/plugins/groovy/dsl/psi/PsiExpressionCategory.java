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

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings({"UnusedDeclaration"})
public class PsiExpressionCategory implements PsiEnhancerCategory{

  @Nullable
  public static PsiClass getClassType(GrExpression expr) {
    final PsiType type = expr.getType();
    return PsiCategoryUtil.getClassType(type, expr);
  }

  /**
   * @return arguments
   */
  public static Collection<GrExpression> getArguments(GrCall call) {
    final GrArgumentList argumentList = call.getArgumentList();
    if (argumentList != null) {
      return Arrays.asList(argumentList.getExpressionArguments());
    }
    return Collections.emptyList();
  }

}
