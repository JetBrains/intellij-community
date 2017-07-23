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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS

/**
 * This extension lowers priority of DefaultGroovyMethods.getAt(Object, String) and DefaultGroovyMethods.putAt(Object, String, Object).
 */
class DGMGetAtMethodFilter : GrMethodComparator() {

  override fun dominated(result1: GroovyMethodResult, result2: GroovyMethodResult, context: Context): Boolean? {
    return when {
      checkMethod(result1.element.extensionMethodOrSelf()) -> true
      checkMethod(result2.element.extensionMethodOrSelf()) -> false
      else -> null
    }
  }

  private fun PsiMethod.extensionMethodOrSelf(): PsiMethod = if (this is GrGdkMethod) staticMethod else this

  private fun checkMethod(method: PsiMethod): Boolean {
    if (method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) return false
    val parameters = method.parameterList.parameters
    val name = method.name
    if (name == "getAt" && parameters.size == 2) {
      return parameters[0].type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
             parameters[1].type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
    }
    else if (name == "putAt" && parameters.size == 3) {
      return parameters[0].type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
             parameters[1].type.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
             parameters[2].type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
    }
    return false
  }
}