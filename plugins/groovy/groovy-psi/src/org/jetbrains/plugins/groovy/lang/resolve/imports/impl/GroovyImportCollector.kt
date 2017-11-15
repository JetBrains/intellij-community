// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.openapi.util.text.StringUtil.getPackageName
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.util.reverse
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.Import
import org.jetbrains.plugins.groovy.lang.resolve.ImportType
import org.jetbrains.plugins.groovy.lang.resolve.imports.*

class GroovyImportCollector(private val file: GroovyFile) {

  private val statementToImport = mutableMapOf<GrImportStatement, GroovyImport>()
  private val imports = HashMap<ImportKind<*>, LinkedHashMap<String, GroovyImport>>()

  @Suppress("UNCHECKED_CAST")
  private fun <T : GroovyImport> getMap(kind: ImportKind<T>): LinkedHashMap<String, T> {
    val map = imports.getOrPut(kind) { LinkedHashMap() }
    return map as LinkedHashMap<String, T>
  }

  private fun addImport(import: RegularImport) {
    getMap(ImportKind.Regular)[import.name] = import
  }

  private fun addImport(import: StaticImport) {
    getMap(ImportKind.Static)[import.name] = import
  }

  private fun addImport(import: StarImport) {
    getMap(ImportKind.Star)[import.packageFqn] = import
  }

  private fun addImport(import: StaticStarImport) {
    getMap(ImportKind.StaticStar)[import.classFqn] = import
  }

  private fun addImport(import: GroovyImport) = when (import) {
    is RegularImport -> addImport(import)
    is StaticImport -> addImport(import)
    is StarImport -> addImport(import)
    is StaticStarImport -> addImport(import)
    else -> error("Unsupported import. Class: ${import.javaClass}; toString: ${import}")
  }

  internal fun addImportFromStatement(statement: GrImportStatement) {
    val import = statement.import ?: return
    statementToImport[statement] = import
    addImport(import)
  }

  internal fun addImportFromContributor(contributedImport: Import) {
    val name = contributedImport.name
    when (contributedImport.type) {
      ImportType.REGULAR -> addImport(RegularImport(name, getShortName(name)))
      ImportType.STATIC -> addImport(StaticImport(getPackageName(name), getShortName(name)))
      ImportType.STAR -> addImport(StarImport(name))
      ImportType.STATIC_STAR -> addImport(StaticStarImport(name))
    }
  }

  internal fun build(): GroovyFileImports = GroovyFileImportsImpl(
    file,
    imports.mapValues { (_, map) -> map.values.toList() }.toMap(),
    statementToImport,
    statementToImport.reverse()
  )
}
