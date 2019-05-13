// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport

@Deprecated("see org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport")
class Import(
  val name: String,
  @Suppress("DEPRECATION")
  val type: ImportType = ImportType.REGULAR
)

@Deprecated("see org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport")
enum class ImportType {
  /**
   * Class
   */
  REGULAR,
  /**
   * Class member
   */
  STATIC,
  /**
   * Classes of package
   */
  STAR,
  /**
   * Members of class
   */
  STATIC_STAR
}

@Deprecated("use org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor")
abstract class GrImportContributorBase : GrImportContributor {

  abstract fun appendImplicitlyImportedPackages(file: GroovyFile): List<String>

  final override fun getFileImports(file: GroovyFile): List<GroovyImport> = appendImplicitlyImportedPackages(file).map(::StarImport)
}
