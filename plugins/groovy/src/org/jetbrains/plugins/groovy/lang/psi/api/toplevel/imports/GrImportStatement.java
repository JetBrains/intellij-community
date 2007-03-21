package org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Ilya.Sergey
 */
public interface GrImportStatement extends UserDataHolderEx, Cloneable, Iconable, NavigationItem, GroovyPsiElement {
}
