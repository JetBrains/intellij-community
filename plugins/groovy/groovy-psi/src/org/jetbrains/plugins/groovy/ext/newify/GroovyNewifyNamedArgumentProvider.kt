/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.lang.GroovyConstructorNamedArgumentProvider
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

class GroovyNewifyNamedArgumentProvider : GroovyConstructorNamedArgumentProvider() {
  override fun getCorrespondingClasses(call: GrCall, resolveResult: GroovyResolveResult): List<PsiClass> {
    val resolved = (resolveResult.element as? NewifyMemberContributor.NewifiedConstructor) ?: return emptyList()
    return resolved.containingClass?.let { listOf(it) } ?: emptyList()
  }
}
