/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.List;

public class GroovyExpressionTypeProvider extends ExpressionTypeProvider<GrExpression> {

  @NotNull
  @Override
  public String getInformationHint(@NotNull GrExpression element) {
    final PsiType type = element.getType();
    return StringUtil.escapeXml(type == null ? "<unknown>" : type.getPresentableText());
  }

  @NotNull
  @Override
  public String getErrorHint() {
    return "No expression found";
  }

  @NotNull
  @Override
  public List<GrExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return GrIntroduceHandlerBase.collectExpressions(elementAt, true);
  }
}
