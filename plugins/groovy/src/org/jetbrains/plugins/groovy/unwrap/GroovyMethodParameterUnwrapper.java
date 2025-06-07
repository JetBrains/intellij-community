// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.List;

public class GroovyMethodParameterUnwrapper extends GroovyUnwrapper {
  public GroovyMethodParameterUnwrapper() {
    super("");
  }

  @Override
  public @NotNull String getDescription(@NotNull PsiElement e) {
    String text = e.getText();
    if (text.length() > 20) text = text.substring(0, 17) + "...";
    return CodeInsightBundle.message("unwrap.with.placeholder", text);
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return (e instanceof GrExpression) && e.getParent() instanceof GrArgumentList;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement methodCall = element.getParent().getParent();
    context.extractElement(element, methodCall);

    context.deleteExactly(methodCall);
  }
}
