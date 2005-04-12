/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 09.04.2005
 * Time: 22:37:50
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public interface Property extends PsiNamedElement {
  PropertyKey getKey();
  PropertyValue getValue();
  PsiElement getKeyValueSeparator();

}