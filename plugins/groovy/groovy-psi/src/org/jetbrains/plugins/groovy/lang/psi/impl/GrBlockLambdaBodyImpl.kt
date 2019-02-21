// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrBlockLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer

class GrBlockLambdaBodyImpl(type: IElementType, buffer: CharSequence?) : GrBlockImpl(type, buffer), GrBlockLambdaBody {

  override fun getReturnType(): PsiType? = GroovyPsiManager.inferType(this, MethodTypeInferencer(this))

  override fun isTopControlFlowOwner(): Boolean = true

  override fun getLambdaExpression(): GrLambdaExpression = requireNotNull(parentOfType())

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitBlockLambdaBody(this)

  override fun toString(): String = "Lambda body block"
}
