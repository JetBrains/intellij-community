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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import com.intellij.lang.ASTNode;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

/**
 * author ven
 */
public class GrLogicalExpressionImpl extends GrBinaryExpressionImpl {
  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      return binary.getTypeByFQName(CommonClassNames.JAVA_LANG_BOOLEAN);
    }
  };

  public GrLogicalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return TYPE_CALCULATOR;
  }

  @Override
  public String toString() {
    return "Logical expression";
  }
}
