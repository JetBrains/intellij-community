/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock

@JvmField val DELEGATES_TO_KEY = Key.create<String>("groovy.closure.delegatesTo.type")
@JvmField val DELEGATES_TO_STRATEGY_KEY = Key.create<Int>("groovy.closure.delegatesTo.strategy")

fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? = CachedValuesManager.getCachedValue(closure) {
  Result.create(doGetDelegatesToInfo(closure), PsiModificationTracker.MODIFICATION_COUNT)
}

private fun doGetDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
  return GrDelegatesToProvider.EP_NAME.extensions.asSequence().mapNotNull { it.getDelegatesToInfo(closure) }.firstOrNull()
}

