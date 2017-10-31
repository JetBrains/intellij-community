// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class Import(
    val name: String,
    val type: ImportType = ImportType.REGULAR
)

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

abstract class GrImportContributorBase : GrImportContributor {

  abstract fun appendImplicitlyImportedPackages(file: GroovyFile): List<String>

  final override fun getImports(file: GroovyFile): Collection<Import> {
    return appendImplicitlyImportedPackages(file).map {
      Import(it, ImportType.STAR)
    }
  }
}
