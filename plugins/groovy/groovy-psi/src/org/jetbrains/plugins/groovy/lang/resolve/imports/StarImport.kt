// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.resolve
import org.jetbrains.plugins.groovy.lang.resolve.isNonAnnotationResolve
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessClasses

data class StarImport(val packageFqn: String) : GroovyStarImport {

  override val fqn: String get() = packageFqn

  override fun resolveImport(file: GroovyFileBase): PsiPackage? = file.resolve(this) {
    JavaPsiFacade.getInstance(file.project).findPackage(packageFqn)
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean {
    if (processor.isNonAnnotationResolve()) return true
    if (!processor.shouldProcessClasses()) return true
    val pckg = resolveImport(file) ?: return true
    return pckg.processDeclarations(processor, state, null, place)
  }

  override fun isUnnecessary(imports: GroovyFileImports): Boolean = this in defaultStarImportsSet

  @NonNls
  override fun toString(): String = "import $packageFqn.*"
}
