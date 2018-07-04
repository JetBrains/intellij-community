// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference

fun referenceArray(right: GroovyReference?, left: GroovyReference?): Array<out GroovyReference> {
  return if (left == null) {
    if (right == null) {
      GroovyReference.EMPTY_ARRAY
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

fun GrReferenceElement<*>.resolvePackageFqn(): PsiPackage? {
  val fqn = qualifiedReferenceName ?: return null
  return JavaPsiFacade.getInstance(project).findPackage(fqn)
}

/**
 * @see org.codehaus.groovy.control.ResolveVisitor.testVanillaNameForClass
 */
internal val String.isVanillaClassName: Boolean get() = isNotEmpty() && !Character.isLowerCase(this[0])
