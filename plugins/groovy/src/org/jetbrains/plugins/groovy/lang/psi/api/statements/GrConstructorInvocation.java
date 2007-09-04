package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public interface GrConstructorInvocation extends GrStatement, PsiReference {
  GrArgumentList getArgumentList();

  boolean isSuperCall();

  boolean isThisCall();

  PsiElement getThisOrSuperKeyword();

  PsiMethod resolveConstructor();

  PsiClass getDelegatedClass();

  GroovyResolveResult[] multiResolveConstructor();
}
