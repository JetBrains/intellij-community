// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.GrImportContributor
import org.jetbrains.plugins.groovy.lang.resolve.Import
import org.jetbrains.plugins.groovy.lang.resolve.ImportType
import org.jetbrains.plugins.groovy.lang.resolve.imports.*

internal val defaultRegularImports = GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES.map(::RegularImport)
internal val defaultStarImports = GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES.map(::StarImport)

internal fun GroovyFile.doGetImports(): GroovyFileImports {
  val regularImports = LinkedHashMap<String, RegularImport>()
  val starImports = LinkedHashMap<String, StarImport>()
  val staticImports = LinkedHashMap<String, StaticImport>()
  val staticStarImports = LinkedHashMap<String, StaticStarImport>()

  fun addImport(import: GroovyImport?) {
    when (import) {
      is RegularImport -> regularImports[import.name] = import
      is StaticImport -> staticImports[import.name] = import
      is StarImport -> starImports[import.packageFqn] = import
      is StaticStarImport -> staticStarImports[import.classFqn] = import
      null -> return
      else -> unsupportedImport(import)
    }
  }

  for (statement in importStatements) {
    addImport(statement.import)
  }

  for (contributor in GrImportContributor.EP_NAME.extensions) {
    contributor.getImports(this).forEach {
      addImport(createImportFromContributor(it))
    }
  }

  return GroovyFileImportsImpl(
    regularImports.values.toList(),
    staticImports.values.toList(),
    starImports.values.toList(),
    staticStarImports.values.toList()
  )
}

private fun createImportFromContributor(contributedImport: Import): GroovyImport {
  val type = contributedImport.type
  val name = contributedImport.name
  return when (type) {
    ImportType.REGULAR -> RegularImport(name)
    ImportType.STATIC -> {
      val packageName = StringUtil.getPackageName(name)
      val memberName = StringUtil.getShortName(name)
      StaticImport(packageName, memberName)
    }
    ImportType.STAR -> StarImport(name)
    ImportType.STATIC_STAR -> StaticStarImport(name)
  }
}

private fun unsupportedImport(import: GroovyImport): Nothing = error("Unsupported import. Class: ${import.javaClass}; toString: ${import}")
