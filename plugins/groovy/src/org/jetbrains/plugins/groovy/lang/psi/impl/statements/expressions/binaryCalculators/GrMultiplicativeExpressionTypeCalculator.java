/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrMultiplicativeExpressionTypeCalculator extends GrNumericBinaryExpressionTypeCalculator {
  public static final GrMultiplicativeExpressionTypeCalculator INSTANCE = new GrMultiplicativeExpressionTypeCalculator();

  @Nullable
  @Override
  protected PsiType inferNumericType(@NotNull PsiType ltype, @NotNull PsiType rtype, GrBinaryFacade e) {
    return GrBinaryExpressionUtil.getDefaultNumericResultType(ltype, rtype, e);
  }
}
