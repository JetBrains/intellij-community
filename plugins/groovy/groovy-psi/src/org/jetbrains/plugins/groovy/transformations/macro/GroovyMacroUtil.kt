// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil

internal fun getAvailableMacroSupport(call: GrCall): GroovyMacroTransformationSupport? {
  if (!GdkMethodUtil.isMacro(call.resolveMethod())) {
    return null
  }
  return CachedValuesManager.getCachedValue(call) {
    CachedValueProvider.Result(doGetAvailableMacros(call), call)
  }
}

private val EP_NAME: ExtensionPointName<GroovyMacroTransformationSupport>
= ExtensionPointName.create("org.intellij.groovy.macroTransformationSupport")

private fun doGetAvailableMacros(call: GrCall): GroovyMacroTransformationSupport? {
  val available = EP_NAME.extensionList.filter { it.isApplicable(call) }
  if (available.size > 1) {
    logger<GroovyMacroTransformationSupport>().error(
      "Multiple handler for the macro ${call.resolveMethod()?.name}: ${available.joinToString { it.javaClass.name }}")
  }
  return available.singleOrNull()
}
