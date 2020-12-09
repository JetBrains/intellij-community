// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class GroovyImplicitNullArgumentCallInspection : BaseInspection() {

  override fun buildErrorString(vararg args: Any?): String {
    return GroovyBundle.message("inspection.message.method.called.with.implicit.null.argument")
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitArgumentList(list: GrArgumentList) {
      if (list.allArguments.isNotEmpty()) {
        return
      }
      val call = list.parentOfType<GrCall>()?.takeIf { it.argumentList === list } ?: return
      if (PsiUtil.isEligibleForInvocationWithNull(call)) {
        registerError(list, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
      super.visitArgumentList(list)
    }
  }
}
