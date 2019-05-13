// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ArrayUtil
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

@JvmField val DELEGATES_TO_KEY: Key<String> = Key.create<String>("groovy.closure.delegatesTo.type")
@JvmField val DELEGATES_TO_STRATEGY_KEY: Key<Int> = Key.create<Int>("groovy.closure.delegatesTo.strategy")

val defaultDelegatesToInfo: DelegatesToInfo = DelegatesToInfo(null, Closure.OWNER_ONLY)

fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? = CachedValuesManager.getCachedValue(closure) {
  Result.create(doGetDelegatesToInfo(closure), PsiModificationTracker.MODIFICATION_COUNT)
}

private fun doGetDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
  for (ext in GrDelegatesToProvider.EP_NAME.extensions) {
    val info = ext.getDelegatesToInfo(closure)
    if (info != null) {
      return info
    }
  }
  return null
}

fun getContainingCall(closableBlock: GrClosableBlock): GrCall? {
  val parent = closableBlock.parent
  if (parent is GrCall && ArrayUtil.contains(closableBlock, *parent.closureArguments)) {
    return parent
  }

  if (parent is GrArgumentList) {
    val grandParent = parent.parent
    return grandParent as? GrCall
  }

  return null
}
