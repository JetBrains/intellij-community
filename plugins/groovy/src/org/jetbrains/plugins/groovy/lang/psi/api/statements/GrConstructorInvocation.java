package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public interface GrConstructorInvocation extends GrStatement, GrConstructorCall, PsiReference {
  boolean isSuperCall();

  boolean isThisCall();

  PsiElement getThisOrSuperKeyword();

  PsiClass getDelegatedClass();
}
