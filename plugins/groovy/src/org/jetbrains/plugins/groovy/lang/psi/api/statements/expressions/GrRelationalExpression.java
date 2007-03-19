package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.NavigationItem;

/**
 * @author Ilya.Sergey
 */
public interface GrRelationalExpression extends UserDataHolderEx, Cloneable, Iconable, PsiElement, NavigationItem, GroovyPsiElement {
  public String toString();
}