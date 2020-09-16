// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectNamedParamsFromNamedVariantMethod

class GrNamedVariantLabelsInspection : BaseInspection() {

  override fun buildErrorString(vararg args: Any?): String {
    return GroovyBundle.message("inspection.message.label.name.ref.not.supported.by.0", args[0])
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitMethodCall(call: GrMethodCall) {
      val namedArguments = call.namedArguments.takeIf { it.isNotEmpty() } ?: return
      val resolvedMethod = call.resolveMethod() as? GrMethod ?: return
      val definedNames = collectNamedParamsFromNamedVariantMethod(resolvedMethod).mapTo(HashSet(namedArguments.size)) { it.name }
      for (namedArg in namedArguments) {
        val label = namedArg.label ?: continue
        if (namedArg?.labelName in definedNames) continue
        registerError(label, "@NamedVariant")
      }
    }
  }
}