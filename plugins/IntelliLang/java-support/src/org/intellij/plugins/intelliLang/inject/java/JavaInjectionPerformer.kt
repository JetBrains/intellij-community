// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.java

import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.JavaConcatenationToInjectorAdapter
import com.intellij.util.SmartList
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.InjectorUtils.InjectionInfo
import org.intellij.plugins.intelliLang.inject.registerSupport

class JavaInjectionPerformer : LanguageInjectionPerformer {
  override fun isPrimary(): Boolean = true

  override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
    val injectorAdapter = JavaConcatenationToInjectorAdapter(context.project)
    val (_, operands) = injectorAdapter.computeAnchorAndOperands(context)

    if (operands.isEmpty()) return false
    val containingFile = context.containingFile

    val injectedLanguage = InjectedLanguage.create(injection.injectedLanguageId,
                                                   injection.prefix,
                                                   injection.suffix, false) ?: return false

    val language = injectedLanguage.language ?: return false

    val infos = SmartList<InjectionInfo>()
    var pendingPrefix = injectedLanguage.prefix
    for (operand in operands.slice(0 until operands.size - 1)) {
      if (operand !is PsiLanguageInjectionHost) continue
      val injectionPart = InjectedLanguage.create(injection.injectedLanguageId,
                                                  pendingPrefix,
                                                  null, false) ?: continue
      pendingPrefix = ""
      infos.add(InjectionInfo(operand, injectionPart, ElementManipulators.getValueTextRange(operand)))
    }

    InjectedLanguage.create(injection.injectedLanguageId,
                            pendingPrefix,
                            injectedLanguage.suffix, false)?.let { injectionPart ->
      val operand = operands.last() as? PsiLanguageInjectionHost ?: return@let
      infos.add(InjectionInfo(operand, injectionPart, ElementManipulators.getValueTextRange(operand)))
    }

    InjectorUtils.registerInjection(language, containingFile, infos, registrar) { registrar ->
      injection.supportId
        ?.let { InjectorUtils.findInjectionSupport(it) }
        ?.let { registrar.registerSupport(it, false) }
    }
    return true
  }
}