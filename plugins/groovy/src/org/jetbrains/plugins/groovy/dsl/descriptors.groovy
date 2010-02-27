package org.jetbrains.plugins.groovy.dsl

import com.intellij.psi.PsiElement

/**
 * @author peter
 */

interface ClassDescriptor {

  String getQualifiedName()

  boolean isInheritor(String qname)

  PsiElement getPlace();  

}