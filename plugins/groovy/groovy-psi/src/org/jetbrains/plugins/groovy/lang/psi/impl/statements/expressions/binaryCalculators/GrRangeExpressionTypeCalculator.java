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

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;

/**
* Created by Max Medvedev on 12/20/13
*/
public class GrRangeExpressionTypeCalculator extends GrBinaryExpressionTypeCalculator {
  public static final GrBinaryExpressionTypeCalculator INSTANCE = new GrRangeExpressionTypeCalculator();

  @Override
  public PsiType fun(GrBinaryFacade e) {
    final PsiType type = super.fun(e);
    if (type != null) return type;

    final PsiType ltype = GrBinaryExpressionUtil.getLeftType(e);
    final PsiType rtype = GrBinaryExpressionUtil.getRightType(e);

    return new GrRangeType(e.getPsiElement().getResolveScope(), JavaPsiFacade.getInstance(e.getPsiElement().getProject()), ltype, rtype);
  }
}
