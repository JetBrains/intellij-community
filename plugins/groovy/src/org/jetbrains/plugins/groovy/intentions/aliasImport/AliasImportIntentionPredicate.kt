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
package org.jetbrains.plugins.groovy.intentions.aliasImport

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

object AliasImportIntentionPredicate : PsiElementPredicate {

  override fun satisfiedBy(element: PsiElement): Boolean {
    if (element is GrReferenceExpression) {
      val result = element.advancedResolve()
      val context = result.currentFileResolveContext as? GrImportStatement ?: return false
      if (!context.isStatic || context.isAliasedImport) return false
      return true
    }
    else if (element is GrImportStatement) {
      if (!element.isStatic || element.isAliasedImport || element.isOnDemand) return false
      val reference = element.importReference ?: return false
      return reference.resolve() is PsiMember
    }
    else return false
  }
}
