// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result.create
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl

class GrLambdaExpressionImpl(node: ASTNode) : GrExpressionImpl(node), GrLambdaExpression {

  override fun getParameters(): Array<GrParameter> {
    return parameterList.parameters
  }

  override fun getParameterList(): GrParameterList = findNotNullChildByClass(GrParameterListImpl::class.java)

  override fun accept(visitor: GroovyElementVisitor) = visitor.visitLambdaExpression(this)

  override fun isVarArgs(): Boolean = false

  override fun getBody(): PsiElement? {
    val body = lastChild
    return if (body is GrExpression || body is GrCodeBlock) body else null
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    return processParameters(processor, state) && processClosureClassMembers(processor, state, lastParent, place)
  }

  override fun getAllParameters(): Array<GrParameter> = parameters

  override fun getOwnerType(): PsiType? {
    return getCachedValue(this) {
      create(doGetOwnerType(), PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  override fun getReturnType(): PsiType? = TypeInferenceHelper.getCurrentContext().getExpressionType(this, ::calculateReturnType)

  override fun getType(): PsiType? {
    return GrClosureType.create(this, true)
  }

  override fun toString(): String = "Lambda expression"
}
