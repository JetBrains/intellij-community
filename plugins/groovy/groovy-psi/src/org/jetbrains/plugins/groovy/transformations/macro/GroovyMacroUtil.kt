// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

internal fun getAvailableMacroSupport(call: GrMethodCall): GroovyMacroTransformationSupport? {
  val macroMethod = call.project.service<GroovyMacroRegistryService>().resolveAsMacro(call) ?: return null
  return CachedValuesManager.getCachedValue(call) {
    CachedValueProvider.Result(doGetAvailableMacroSupport(call), call)
  }
}

private val EP_NAME: ExtensionPointName<GroovyMacroTransformationSupport>
= ExtensionPointName.create("org.intellij.groovy.macroTransformationSupport")

private fun doGetAvailableMacroSupport(call: GrMethodCall): GroovyMacroTransformationSupport? {
  val available = EP_NAME.extensionList.filter { it.isApplicable(call) }
  if (available.size > 1) {
    logger<GroovyMacroTransformationSupport>()
      .error("Ambiguous handlers for the macro ${call.resolveMethod()?.name}: ${available.joinToString { it.javaClass.name }}")
  }
  return available.singleOrNull()
}

fun getMacroHandler(element: PsiElement) : Pair<GrMethodCall, GroovyMacroTransformationSupport>? {
  return element.parentsOfType<GrMethodCall>().mapNotNull { getAvailableMacroSupport(it)?.let(it::to) }.firstOrNull()
}

fun getTypeFromMacro(expr: GrExpression): PsiType? {
  val support = getMacroHandler(expr)
  if (support != null) {
    val macroType = support.second.computeType(support.first, expr)
    if (macroType != null) {
      return macroType
    }
  }
  return null
}
