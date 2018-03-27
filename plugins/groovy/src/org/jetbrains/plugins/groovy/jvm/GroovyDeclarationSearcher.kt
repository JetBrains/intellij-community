// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.jvm

import com.intellij.lang.jvm.JvmElement
import com.intellij.lang.jvm.source.JvmDeclarationSearcher
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getMethodOrReflectedMethods
import org.jetbrains.plugins.groovy.util.plusAssign
import java.util.function.Consumer

class GroovyDeclarationSearcher : JvmDeclarationSearcher {

  override fun isDeclaringElement(identifierElement: PsiElement, declaringElement: PsiElement): Boolean {
    val parent = identifierElement.parent as? GrNamedElement
    return parent != null && parent.nameIdentifierGroovy == identifierElement
  }

  override fun findDeclarations(declaringElement: PsiElement, consumer: Consumer<in JvmElement>) {
    if (declaringElement is GrMethod) {
      consumer += getMethodOrReflectedMethods(declaringElement)
    }
    else if (declaringElement is GrNamedElement && declaringElement is JvmElement) {
      consumer += declaringElement
    }
  }
}
