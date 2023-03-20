// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.jvm

import com.intellij.lang.jvm.JvmElement
import com.intellij.lang.jvm.source.JvmDeclarationSearcher
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getMethodOrReflectedMethods

class GroovyDeclarationSearcher : JvmDeclarationSearcher {

  override fun findDeclarations(declaringElement: PsiElement): Collection<JvmElement> {
    return when {
      declaringElement is GrMethod -> getMethodOrReflectedMethods(declaringElement).toList()
      declaringElement is GrNamedElement && declaringElement is JvmElement -> listOf(declaringElement)
      else -> emptyList()
    }
  }

  override fun adjustIdentifierElement(identifierElement: PsiElement): PsiElement? {
    val parent = identifierElement.parent as? GrAnonymousClassDefinition
    return if (parent?.baseClassReferenceGroovy === identifierElement) parent else null
  }
}
