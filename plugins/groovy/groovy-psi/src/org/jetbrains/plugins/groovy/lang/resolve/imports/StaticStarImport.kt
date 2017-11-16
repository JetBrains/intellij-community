// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("LoopToCallChain")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.NonFqnImport
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMembersFilteringProcessor

data class StaticStarImport(override val classFqn: String) : NonFqnImport(), GroovyStarImport {

  override val fqn: String get() = classFqn

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean {
    val clazz = resolveImport(file) ?: return true
    for (each in GroovyResolverProcessor.allProcessors(processor)) {
      val filteringProcessor = StaticMembersFilteringProcessor(each, null)
      if (!clazz.processDeclarations(filteringProcessor, state, null, place)) return false
    }
    return true
  }

  override fun isUnnecessary(imports: GroovyFileImports): Boolean = false

  override fun toString(): String = "import static $classFqn.*"
}
