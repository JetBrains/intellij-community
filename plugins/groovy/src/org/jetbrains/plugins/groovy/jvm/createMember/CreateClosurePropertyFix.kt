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
package org.jetbrains.plugins.groovy.jvm.createMember

import com.intellij.jvm.JvmClass
import com.intellij.jvm.createMember.CreateJvmMethodRequest
import com.intellij.jvm.createMember.fix.CreateJvmMethodFix
import com.intellij.psi.PsiClassType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class CreateClosurePropertyFix(methodCall: GrMethodCall) : CreateJvmMethodFix<GrMethodCall>(methodCall) {

  override fun createRequest(element: GrMethodCall): CreateJvmMethodRequest? {
    val argumentTypes = PsiUtil.getArgumentTypes(element.argumentList) ?: return null
    val expression = element.invokedExpression as? GrReferenceExpression ?: return null
    val referenceName = expression.referenceName ?: return null
    val getterName = GroovyPropertyUtils.getGetterNameNonBoolean(referenceName)

    return object : CreateJvmMethodRequest(getterName, argumentTypes.toList(), GROOVY_LANG_CLOSURE) {
      override fun getTitle() = "Create Closure property '${referenceName}'"
    }
  }

  override fun collectTargetClasses(element: GrMethodCall): List<JvmClass> {
    val invoked = element.invokedExpression as? GrReferenceExpression
    val type = invoked?.qualifierExpression?.type as? PsiClassType
    val clazz = type?.resolve() ?: return emptyList()
    return clazz.superTypes.mapNotNull {
      it.resolve()
    } + clazz
  }
}