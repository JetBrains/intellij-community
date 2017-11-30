// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassProcessor

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
data class RegularImport(val classFqn: String, override val name: String) : GroovyNamedImport {

  constructor(classFqn: String) : this(classFqn, StringUtil.getShortName(classFqn))

  override val isAliased: Boolean = getShortName(classFqn) != name

  override fun resolveImport(file: GroovyFileBase): PsiClass? {
    if (!file.packageName.isEmpty() && '.' !in classFqn) return null
    val facade = JavaPsiFacade.getInstance(file.project)
    return facade.findClass(classFqn, file.resolveScope)
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean {
    if (processor.isNonAnnotationResolve()) return true
    if (!processor.shouldProcessClasses()) return true
    if (!processor.checkName(name, state)) return true

    val clazz = resolveImport(file) ?: return true
    return processor.execute(clazz, state.put(importedNameKey, name))
  }

  override fun isUnnecessary(imports: GroovyFileImports): Boolean {
    if (isAliased) return false
    val file = imports.file

    val processor = ClassProcessor(name, file)
    val state = ResolveState.initial()

    if (!imports.processStaticImports(processor, state, file)) return false

    val starImport = StarImport(StringUtil.getPackageName(classFqn))
    if (starImport.packageFqn == file.packageName) return true

    if (!file.processClassesInFile(processor, state)) return false
    if (!file.processClassesInPackage(processor, state)) return false

    if (starImport in imports.starImports) return true

    if (!imports.processAllStarImports(processor, state, file)) return false

    val results = processor.results
    assert(results.isEmpty()) {
      "Processor returned true, but there are ${results.size} results: $results"
    }

    return this in defaultRegularImportsSet || starImport in defaultStarImportsSet
  }

  override fun toString(): String = "import $classFqn as $name"
}
