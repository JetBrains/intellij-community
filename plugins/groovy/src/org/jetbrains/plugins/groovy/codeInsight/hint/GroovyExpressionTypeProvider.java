// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.List;

public class GroovyExpressionTypeProvider extends ExpressionTypeProvider<GrExpression> {

  @NotNull
  @Override
  public @HintText String getInformationHint(@NotNull GrExpression element) {
    final PsiType type = element.getType();
    return StringUtil.escapeXmlEntities(type == null ? GroovyBundle.message("expression.type.unknown") : type.getPresentableText());
  }

  @NotNull
  @Override
  public @HintText String getErrorHint() {
    return GroovyBundle.message("expression.type.no.expression");
  }

  @NotNull
  @Override
  public List<GrExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return GrIntroduceHandlerBase.collectExpressions(elementAt, true);
  }
}
