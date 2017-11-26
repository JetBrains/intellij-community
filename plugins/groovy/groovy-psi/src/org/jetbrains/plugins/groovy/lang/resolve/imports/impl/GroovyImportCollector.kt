// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.openapi.util.text.StringUtil.getPackageName
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.util.reverse
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.Import
import org.jetbrains.plugins.groovy.lang.resolve.ImportType
import org.jetbrains.plugins.groovy.lang.resolve.imports.*

class GroovyImportCollector(private val file: GroovyFileBase) {

  private val statementToImport = mutableMapOf<GrImportStatement, GroovyImport>()
  private val imports = HashMap<ImportKind<*>, LinkedHashMap<String, GroovyImport>>()

  val isEmpty get() = imports.values.all { it.isEmpty() }

  fun clear() {
    imports.clear()
  }

  fun setFrom(collector: GroovyImportCollector) {
    imports.clear()
    imports.putAll(collector.imports)
  }

  val allImports: Collection<GroovyImport> get() = imports.values.flatMap { it.values }

  @Suppress("UNCHECKED_CAST")
  private fun <T : GroovyImport> getMap(kind: ImportKind<T>): LinkedHashMap<String, T> {
    val map = imports.getOrPut(kind) { LinkedHashMap() }
    return map as LinkedHashMap<String, T>
  }

  private fun addRegularImport(import: RegularImport) {
    getMap(ImportKind.Regular)[import.name] = import
  }

  private fun addStaticImport(import: StaticImport) {
    getMap(ImportKind.Static)[import.name] = import
  }

  private fun addStarImport(import: StarImport) {
    getMap(ImportKind.Star)[import.packageFqn] = import
  }

  private fun addStaticStarImport(import: StaticStarImport) {
    getMap(ImportKind.StaticStar)[import.classFqn] = import
  }

  private fun addImport(import: GroovyImport) = when (import) {
    is RegularImport -> addRegularImport(import)
    is StaticImport -> addStaticImport(import)
    is StarImport -> addStarImport(import)
    is StaticStarImport -> addStaticStarImport(import)
    else -> error("Unsupported import. Class: ${import.javaClass}; toString: ${import}")
  }

  fun addRegularImport(classFqn: String, name: String) = addRegularImport(RegularImport(classFqn, name))

  fun addStaticImport(classFqn: String, memberName: String) = addStaticImport(classFqn, memberName, memberName)

  fun addStaticImport(classFqn: String, memberName: String, name: String) = addStaticImport(StaticImport(classFqn, memberName, name))

  fun addStarImport(packageFqn: String) = addStarImport(StarImport(packageFqn))

  fun addStaticStarImport(classFqn: String) = addStaticStarImport(StaticStarImport(classFqn))

  internal fun addImportFromStatement(statement: GrImportStatement) {
    val import = statement.import ?: return
    statementToImport[statement] = import
    addImport(import)
  }

  internal fun addImportFromContributor(contributedImport: Import) {
    val name = contributedImport.name
    when (contributedImport.type) {
      ImportType.REGULAR -> addRegularImport(name, getShortName(name))
      ImportType.STATIC -> addStaticImport(getPackageName(name), getShortName(name))
      ImportType.STAR -> addStarImport(name)
      ImportType.STATIC_STAR -> addStaticStarImport(name)
    }
  }

  fun build(): GroovyFileImports = GroovyFileImportsImpl(
    file,
    imports.mapValues { (_, map) -> map.values.toList() }.toMap(),
    statementToImport,
    statementToImport.reverse()
  )
}
