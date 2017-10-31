// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("LoopToCallChain")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.NonFqnImport
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMembersFilteringProcessor

class StaticStarImport internal constructor(
  override val statement: GrImportStatement?,
  override val classFqn: String
) : NonFqnImport(), GroovyStarImport {

  constructor(classFqn: String) : this(null, classFqn)

  override val fqn: String get() = classFqn

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFile): Boolean {
    val clazz = resolve(file) ?: return true
    val stateWithContext = state.put(ClassHint.RESOLVE_CONTEXT, statement)
    for (each in GroovyResolverProcessor.allProcessors(processor)) {
      val filteringProcessor = StaticMembersFilteringProcessor(each, null)
      if (!clazz.processDeclarations(filteringProcessor, stateWithContext, null, place)) return false
    }
    return true
  }

  override fun toString(): String = "import static $classFqn.*"
}
