// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.*
import com.intellij.util.ThreeState
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.evaluateString

private class ActionIdReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerUastReferenceProvider(
      injectionHostUExpression()
        .sourcePsiFilter { PsiUtil.isPluginProject(it.getProject()) }
        .methodCallParameter(0,
                             PsiJavaPatterns.psiMethod()
                               .withName("getAction").definedInClass("com.intellij.openapi.actionSystem.ActionManager")
        ),
      uastInjectionHostReferenceProvider { uhost, host ->
        arrayOf(ActionOrGroupIdReference(
          host,
          ElementManipulators.getValueTextRange(host),
          uhost.evaluateString() ?: ElementManipulators.getValueText(host),
          ThreeState.UNSURE
        ))
      }
    )
  }
}
