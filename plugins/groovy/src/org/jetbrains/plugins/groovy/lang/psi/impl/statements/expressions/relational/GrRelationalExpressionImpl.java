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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMPARE_TO;

/**
 * @author ilyas
 */
public class GrRelationalExpressionImpl extends GrBinaryExpressionImpl {

  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      final String typeName = mCOMPARE_TO == binary.getOperationTokenType() ? JAVA_LANG_INTEGER : JAVA_LANG_BOOLEAN;
      return binary.getTypeByFQName(typeName);
    }
  };

  public GrRelationalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Relational expression";
  }

  @Override
  protected Function<GrBinaryExpressionImpl, PsiType> getTypeCalculator() {
    return TYPE_CALCULATOR;
  }
}