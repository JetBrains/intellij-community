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
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.resolveActualCall

class LogbackDelegatesToProvider : GrDelegatesToProvider {

  private companion object {
    val appenderDelegate = "ch.qos.logback.classic.gaffer.AppenderDelegate"
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val call = getContainingCall(closure) ?: return null

    val result = resolveActualCall(call)
    val method = result.element as? PsiMethod ?: return null
    if (!appenderMethodPattern.accepts(method)) return null

    val signature = GrClosureSignatureUtil.createSignature(method, PsiSubstitutor.EMPTY, true)
    val map = GrClosureSignatureUtil.mapParametersToArguments(
        signature, call.namedArguments, call.expressionArguments, call.closureArguments, closure, false, false
    ) ?: return null


    val paramIndex = map.indexOfFirst { closure in it.args }
    if (paramIndex == 2) {
      return DelegatesToInfo(TypesUtil.createType(appenderDelegate, closure), Closure.DELEGATE_FIRST)
    }

    return null
  }
}