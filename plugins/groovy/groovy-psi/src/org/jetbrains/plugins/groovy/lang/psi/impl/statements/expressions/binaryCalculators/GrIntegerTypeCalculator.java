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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;

public class GrIntegerTypeCalculator implements Function<GrOperatorExpression,PsiType> {
  public static final GrIntegerTypeCalculator INSTANCE = new GrIntegerTypeCalculator();

  @Override
  public PsiType fun(GrOperatorExpression expression) {
    return GrBinaryExpressionUtil.getTypeByFQName(CommonClassNames.JAVA_LANG_INTEGER, expression);
  }
}
