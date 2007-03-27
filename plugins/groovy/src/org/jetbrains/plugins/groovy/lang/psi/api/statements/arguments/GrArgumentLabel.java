package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.NavigationItem;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Ilya.Sergey
 */
public interface GrArgumentLabel extends UserDataHolderEx, Cloneable, Iconable, PsiElement, NavigationItem, GroovyPsiElement {
}
