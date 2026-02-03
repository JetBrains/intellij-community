// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationSupport;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public final class GroovyAnnotationSupport implements PsiAnnotationSupport {
  @Override
  public @NotNull GrLiteral createLiteralValue(@NotNull String value, @NotNull PsiElement context) {
    return (GrLiteral)GroovyPsiElementFactory.getInstance(context.getProject())
      .createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"");
  }
}
