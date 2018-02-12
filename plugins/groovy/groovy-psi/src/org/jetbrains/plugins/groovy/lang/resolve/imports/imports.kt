// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyImports")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import gnu.trove.THashSet
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.GroovyImportCollector
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.RegularImportHashingStrategy
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.StarImportHashingStrategy

internal val defaultRegularImports = GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES.map(::RegularImport)
internal val defaultStarImports = GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES.map(::StarImport)
internal val defaultImports = defaultStarImports + defaultRegularImports
internal val defaultRegularImportsSet = THashSet(defaultRegularImports, RegularImportHashingStrategy)
internal val defaultStarImportsSet = THashSet(defaultStarImports, StarImportHashingStrategy)

val importedNameKey = Key.create<String>("groovy.imported.via.name")

fun GroovyFile.getImports(): GroovyFileImports {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(doGetImports(), this)
  }
}

private fun GroovyFile.doGetImports(): GroovyFileImports {
  val collector = GroovyImportCollector(this)

  for (statement in importStatements) {
    collector.addImportFromStatement(statement)
  }

  for (contributor in GrImportContributor.EP_NAME.extensions) {
    contributor.getFileImports(this).forEach(collector::addImport)
  }

  return collector.build()
}

val GroovyFile.validImportStatements: List<GrImportStatement> get() = importStatements.filterNot(ErrorUtil::containsError)
