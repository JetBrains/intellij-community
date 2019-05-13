// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyStarImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.importKey
import org.jetbrains.plugins.groovy.lang.resolve.isVanillaClassName

internal class ReferenceExpressionClassProcessor(name: String, place: PsiElement) : ClassProcessor(name, place) {

  private val isVanilla = name.isVanillaClassName

  private fun isImportedViaStarImport(state: ResolveState): Boolean = state[importKey] is GroovyStarImport

  private fun skip(state: ResolveState): Boolean = !isVanilla && isImportedViaStarImport(state)

  override fun result(element: PsiElement, state: ResolveState) = if (skip(state)) null else super.result(element, state)
}
