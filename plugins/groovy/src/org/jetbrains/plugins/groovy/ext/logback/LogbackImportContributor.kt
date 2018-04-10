// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport

class LogbackImportContributor : GrImportContributor {

  private val imports: List<GroovyImport> by lazy(::buildImports)

  override fun getFileImports(file: GroovyFile) = if (file.isLogbackConfig()) imports else emptyList()
}
