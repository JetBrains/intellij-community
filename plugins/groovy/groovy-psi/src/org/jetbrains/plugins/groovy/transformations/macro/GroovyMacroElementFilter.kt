// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.lang.GroovyElementFilter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GroovyMacroElementFilter : GroovyElementFilter {
  override fun isFake(element: GroovyPsiElement): Boolean {
    val registry = element.project.service<GroovyMacroRegistryService>()
    val macroCall = element.parentsOfType<GrMethodCall>().find { registry.resolveAsMacro(it) != null }
    if (macroCall == null) return false
    val support = getAvailableMacroSupport(macroCall) ?: return true
    return !support.isUntransformed(macroCall, element)
  }
}