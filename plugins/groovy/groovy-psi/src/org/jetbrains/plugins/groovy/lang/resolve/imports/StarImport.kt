// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint

class StarImport internal constructor(
  override val statement: GrImportStatement?,
  val packageFqn: String
) : GroovyStarImport {

  constructor(packageFqn: String) : this(null, packageFqn)

  override val fqn: String get() = packageFqn

  override fun resolve(file: GroovyFile): PsiPackage? {
    val facade = JavaPsiFacade.getInstance(file.project)
    return facade.findPackage(packageFqn)
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFile): Boolean {
    val pckg = resolve(file) ?: return true
    return pckg.processDeclarations(processor, state.put(ClassHint.RESOLVE_CONTEXT, statement), null, place)
  }

  override fun toString(): String = "import $packageFqn.*"
}
