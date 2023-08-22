// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.NonFqnImport
import org.jetbrains.plugins.groovy.lang.resolve.isAnnotationResolve
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMembersFilteringProcessor
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMembers

data class StaticStarImport(override val classFqn: String) : NonFqnImport(), GroovyStarImport {

  override val fqn: String get() = classFqn

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean {
    if (processor.isAnnotationResolve()) return true
    if (!processor.shouldProcessMembers()) return true
    val clazz = resolveImport(file) ?: return true
    val filteringProcessor = StaticMembersFilteringProcessor(processor, null)
    return clazz.processDeclarations(filteringProcessor, state, null, place)
  }

  override fun isUnnecessary(imports: GroovyFileImports): Boolean = false

  @NonNls
  override fun toString(): String = "import static $classFqn.*"
}
