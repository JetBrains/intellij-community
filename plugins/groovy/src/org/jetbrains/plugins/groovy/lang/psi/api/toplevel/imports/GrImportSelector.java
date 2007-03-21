package org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports;

import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.NavigationItem;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Ilya.Sergey
 */
public interface GrImportSelector extends UserDataHolderEx, Cloneable, Iconable, PsiElement, NavigationItem, GroovyPsiElement {
}
