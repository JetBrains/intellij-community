package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationSupport;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovyAnnotationSupport implements PsiAnnotationSupport {
  @NotNull
  public GrLiteral createLiteralValue(@NotNull String value, @NotNull PsiElement context) {
    return (GrLiteral)GroovyPsiElementFactory.getInstance(context.getProject())
      .createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"");
  }
}
