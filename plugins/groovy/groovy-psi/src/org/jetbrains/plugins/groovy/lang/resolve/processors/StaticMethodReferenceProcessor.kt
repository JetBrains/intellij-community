// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

internal class StaticMethodReferenceProcessor(methodName: String) : MethodReferenceProcessor(methodName) {

  override fun result(method: PsiMethod, state: ResolveState): GroovyResolveResult? {
    return if (method.hasModifier(JvmModifier.STATIC)) super.result(method, state) else null
  }
}
