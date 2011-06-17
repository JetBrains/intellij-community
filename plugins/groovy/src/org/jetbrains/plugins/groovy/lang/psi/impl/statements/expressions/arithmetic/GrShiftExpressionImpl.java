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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

/**
 * @author ilyas
 */
public class GrShiftExpressionImpl extends GrBinaryExpressionImpl {

  private static class MyTypeCalculator implements Function<GrBinaryExpressionImpl, PsiType> {
    private static final Function<GrBinaryExpressionImpl, PsiType> INSTANCE = new MyTypeCalculator();

    @Nullable
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      PsiType lopType = binary.getLeftOperand().getType();
      if (lopType == null) return null;
      if (lopType.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
          lopType.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) ||
          lopType.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
          lopType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
        return binary.getTypeByFQName(CommonClassNames.JAVA_LANG_INTEGER);
      }
      return null;
    }
  }

  public GrShiftExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Shift expression";
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return MyTypeCalculator.INSTANCE;
  }
}
