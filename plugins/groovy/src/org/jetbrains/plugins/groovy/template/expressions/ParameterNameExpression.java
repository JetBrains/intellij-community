// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template.expressions;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public abstract class ParameterNameExpression extends Expression {
  @Override
  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
    SuggestedNameInfo info = getNameInfo(context);
    if (info == null) return new TextResult("p");
    String[] names = info.names;
    if (names.length > 0) {
      return new TextResult(names[0]);
    }
    return null;
  }

  @Nullable
  public abstract SuggestedNameInfo getNameInfo(ExpressionContext context);

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    SuggestedNameInfo info = getNameInfo(context);
    if (info == null) return null;
    LookupElement[] result = new LookupElement[info.names.length];
    int i = 0;
    for (String name : info.names) {
      result[i++] = LookupElementBuilder.create(name);
    }
    return result;
  }
}