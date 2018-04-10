// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.contexts

private fun PsiElement.isOwner(): Boolean = when (this) {
  is GrTypeDefinition, is GrClosableBlock -> true
  is GroovyFile -> context != null
  else -> false
}

/**
 * Returns an immediate owner, which can be one of the following:
 * - class
 * - closure
 * - file
 *
 * @receiver element which owner is needed
 * @return immediate owner
 */
fun PsiElement.getOwner(): PsiElement? = contexts().firstOrNull {
  it.isOwner()
}
