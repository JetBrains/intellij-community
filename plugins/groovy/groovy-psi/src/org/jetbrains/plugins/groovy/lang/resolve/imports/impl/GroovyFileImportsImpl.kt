// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import org.jetbrains.plugins.groovy.lang.resolve.imports.*
import org.jetbrains.plugins.groovy.util.flatten

internal class GroovyFileImportsImpl(
  override val regularImports: Collection<RegularImport>,
  override val staticImports: Collection<StaticImport>,
  override val starImports: Collection<StarImport>,
  override val staticStarImports: Collection<StaticStarImport>
) : GroovyFileImports {

  override val allNamedImports: Collection<GroovyNamedImport> = flatten(regularImports, staticImports)

  override val allStarImports: Collection<GroovyStarImport> = flatten(starImports, staticStarImports)

  override val allImports: Collection<GroovyImport> = flatten(regularImports, staticImports, starImports, staticStarImports)

  override fun toString(): String = "Regular: ${regularImports.size}; static: ${staticImports.size}; " +
                                    "*: ${starImports.size}; static *: ${staticStarImports.size}"
}
