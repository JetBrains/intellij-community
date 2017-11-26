// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement

val GrReferenceElement<*>.qualifiedReferenceName: String?
  get() {
    val parts = mutableListOf<String>()
    var current = this
    while (true) {
      val name = current.referenceName ?: return null
      parts.add(name)
      val qualifier = current.qualifier ?: break
      qualifier as? GrReferenceElement<*> ?: return null
      current = qualifier
    }
    return parts.reversed().joinToString(separator = ".")
  }

fun GrReferenceElement<*>.resolveClassFqn(): PsiClass? {
  val fqn = qualifiedReferenceName ?: return null
  return JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope)
}
