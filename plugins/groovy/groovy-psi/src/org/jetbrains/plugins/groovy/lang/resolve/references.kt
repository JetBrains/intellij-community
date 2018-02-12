/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference

fun referenceArray(right: GroovyPolyVariantReference?, left: GroovyPolyVariantReference?): Array<out GroovyPolyVariantReference> {
  return if (left == null) {
    if (right == null) {
      GroovyPolyVariantReference.EMPTY_ARRAY
    }
    else {
      arrayOf(right)
    }
  }
  else {
    if (right == null) {
      arrayOf(left)
    }
    else {
      arrayOf(right, left)
    }
  }
}

fun GrReferenceElement<*>.resolveClassFqn(): PsiClass? {
  val fqn = qualifiedReferenceName ?: return null
  return JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope)
}
