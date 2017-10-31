// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyImports")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.defaultRegularImports
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.defaultStarImports
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.doGetImports

val defaultImports = defaultStarImports + defaultRegularImports

fun GroovyFile.getImports(): GroovyFileImports {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(doGetImports(), this)
  }
}

val GroovyFile.validImportStatements: List<GrImportStatement> get() = importStatements.filterNot(ErrorUtil::containsError)
