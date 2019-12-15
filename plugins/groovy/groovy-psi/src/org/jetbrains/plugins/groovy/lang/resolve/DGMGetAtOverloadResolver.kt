// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.GET_AT
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.PUT_AT
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyOverloadResolver

/**
 * This extension lowers priority of `DefaultGroovyMethods.getAt(Object, String)` and `DefaultGroovyMethods.putAt(Object, String, Object)`.
 *
 * Groovy resolves `map.getAt(string)` to `DGM#getAt(Object, String)` because it has lower argument distance than `DGM#(Map, Object)`.
 * But method with `Object` receiver doesn't have generics, while method with `Map` receiver has,
 * so we artificially lower `Object` overload priority here.
 * Same goes for `DGM#putAt(Object, String, Object)`.
 */
class DGMGetAtOverloadResolver : GroovyOverloadResolver {

  override fun compare(left: GroovyMethodResult, right: GroovyMethodResult): Int {
    val left_ = checkMethod(left.element.extensionMethodOrSelf())
    val right_ = checkMethod(right.element.extensionMethodOrSelf())
    return left_.compareTo(right_)
  }

  private fun PsiMethod.extensionMethodOrSelf(): PsiMethod = if (this is GrGdkMethod) staticMethod else this

  private fun checkMethod(method: PsiMethod): Boolean {
    if (method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) {
      return false
    }
    val parameters = method.parameterList.parameters
    val name = method.name
    if (name == GET_AT && parameters.size == 2) {
      return parameters[0].type.equalsToText(JAVA_LANG_OBJECT) &&
             parameters[1].type.equalsToText(JAVA_LANG_STRING)
    }
    else if (name == PUT_AT && parameters.size == 3) {
      return parameters[0].type.equalsToText(JAVA_LANG_OBJECT) &&
             parameters[1].type.equalsToText(JAVA_LANG_STRING) &&
             parameters[2].type.equalsToText(JAVA_LANG_OBJECT)
    }
    return false
  }
}