// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.NonFqnImport
import org.jetbrains.plugins.groovy.lang.resolve.isAnnotationResolve
import org.jetbrains.plugins.groovy.lang.resolve.processors.StaticMembersFilteringProcessor
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMembers

/**
 * Represents a static import, possibly aliased.
 *
 * Example: in `import static com.Foo.Bar as Baz`:
 * - [classFqn] = `com.Foo`
 * - [memberName] = `Bar`
 * - [name] = `Baz`
 * - [isAliased] = `true`
 *
 * Example: in `import static com.Foo.Bar`:
 * - [classFqn] = `com.Foo`
 * - [memberName] = `Bar`
 * - [name] = `Bar`
 * - [isAliased] = `false`
 */
data class StaticImport constructor(
  override val classFqn: String,
  val memberName: String,
  override val name: String
) : NonFqnImport(), GroovyNamedImport {

  constructor(classFqn: String, memberName: String) : this(classFqn, memberName, memberName)

  override val isAliased: Boolean = memberName != name

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean {
    if (processor.isAnnotationResolve()) return true
    if (!processor.shouldProcessMembers()) return true
    val clazz = resolveImport(file) ?: return true
    val namesMapping = namesMapping()
    val hintName = processor.getName(state)
    for ((memberName, alias) in namesMapping) {
      if (hintName != null && hintName != alias) continue
      val delegate = StaticMembersFilteringProcessor(processor, memberName)
      if (!clazz.processDeclarations(delegate, state.put(importedNameKey, alias), null, place)) return false
    }
    return true
  }

  override fun isUnnecessary(imports: GroovyFileImports): Boolean {
    if (isAliased) return false
    return StaticStarImport(classFqn) in imports.staticStarImports
  }

  override fun toString(): String = "import static $classFqn.$memberName as $name"

  private fun namesMapping() = namesMapping(memberName, name)

  companion object {

    private fun names(name: String): List<String> = listOf(
      name,
      getGetterNameNonBoolean(name),
      getGetterNameBoolean(name),
      getSetterName(name)
    )

    private fun namesMapping(left: String, right: String): List<Pair<String, String>> {
      val leftNames = names(left)
      val rightNames = if (left == right) leftNames
      else names(
        right)
      return leftNames.zip(rightNames)
    }
  }
}
