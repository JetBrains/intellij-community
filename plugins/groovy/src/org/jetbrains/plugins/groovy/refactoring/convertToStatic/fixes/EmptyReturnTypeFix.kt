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
package org.jetbrains.plugins.groovy.refactoring.convertToStatic.fixes

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.groovy.intentions.style.AddReturnTypeFix
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class EmptyReturnTypeFix : BaseFix() {
  override fun visitReferenceExpression(referenceExpression: GrReferenceExpression) {
    val resolveResult = referenceExpression.advancedResolve()
    if (!resolveResult.isAccessible || !resolveResult.isApplicable) return
    val method = resolveResult.element as? GrMethod ?: return
    if (method is LightElement || method.isConstructor) return
    method.returnTypeElementGroovy?.let { return }

    AddReturnTypeFix.applyFix(referenceExpression.project, method)
  }
}