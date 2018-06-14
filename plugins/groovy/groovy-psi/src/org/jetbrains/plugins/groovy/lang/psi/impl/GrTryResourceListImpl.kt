// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList

class GrTryResourceListImpl(node: ASTNode) : GroovyPsiElementImpl(node), GrTryResourceList {

  override fun accept(visitor: GroovyElementVisitor): Unit = visitor.visitTryResourceList(this)

  override fun toString(): String = "Try resource list"
}
