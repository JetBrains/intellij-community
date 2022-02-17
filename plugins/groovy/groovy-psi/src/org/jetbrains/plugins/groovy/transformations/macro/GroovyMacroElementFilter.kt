// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.GroovyElementFilter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil

class GroovyMacroElementFilter : GroovyElementFilter {
  override fun isFake(element: GroovyPsiElement): Boolean {
    return GdkMethodUtil.isMacro(element.parent.castSafelyTo<GrMethodCall>()?.resolveMethod())
  }
}