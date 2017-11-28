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
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

class GrClosureDelegateTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

  override fun getType(expression: GrReferenceExpression): PsiType? {
    val method = expression.resolve() as? PsiMethod ?: return null
    if ("getDelegate" != method.name || method.parameterList.parametersCount != 0) return null

    val closureClass = JavaPsiFacade.getInstance(expression.project).findClass(GROOVY_LANG_CLOSURE, expression.resolveScope)
    if (closureClass == null || closureClass != method.containingClass) return null

    val closure = PsiTreeUtil.getParentOfType(expression, GrClosableBlock::class.java) ?: return null
    return getDelegatesToInfo(closure)?.typeToDelegate
  }
}
