// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.checkName
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessClasses

/**
 * Represents regular import, possibly aliased.
 *
 * Example: in `import com.foo.Bar as Baz`:
 * - [classFqn] = `com.foo.Bar`
 * - [name] = `Baz`
 * - [isAliased] = `true`
 *
 * Example: in `import com.foo.Bar`:
 * - [classFqn] = `com.foo.Bar`
 * - [name] = `Bar`
 * - [isAliased] = `false`
 */

class RegularImport internal constructor(
  override val statement: GrImportStatement?,
  val classFqn: String,
  override val name: String
) : GroovyNamedImport {

  constructor(fqn: String) : this(null, fqn, getShortName(fqn))
  constructor(fqn: String, name: String) : this(null, fqn, name)

  override val isAliased: Boolean = getShortName(classFqn) != name

  override fun resolve(file: GroovyFile): PsiClass? {
    val facade = JavaPsiFacade.getInstance(file.project)
    return facade.findClass(classFqn, file.resolveScope)
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFile): Boolean {
    if (!processor.shouldProcessClasses()) return true
    if (!processor.checkName(name, state)) return true

    val clazz = resolve(file) ?: return true
    return processor.execute(clazz, state.put(ClassHint.RESOLVE_CONTEXT, statement))
  }

  override fun toString(): String = "import $classFqn as $name"
}
