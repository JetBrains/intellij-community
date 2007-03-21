package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.NavigationItem;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Ilya.Sergey
 */
public interface GrString extends UserDataHolderEx, Cloneable, Iconable, PsiElement, NavigationItem, GroovyPsiElement {
}
