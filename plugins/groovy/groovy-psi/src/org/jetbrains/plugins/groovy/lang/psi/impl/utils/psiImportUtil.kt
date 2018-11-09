// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PsiImportUtil")

package org.jetbrains.plugins.groovy.lang.psi.impl.utils

import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.imports.*
import org.jetbrains.plugins.groovy.util.getPackageAndShortName

internal fun GrImportStatement.createImportFromStatement(): GroovyImport? {
  val fqn = importFqn ?: return null
  val static = isStatic
  val star = isOnDemand
  return if (static && star) {
    StaticStarImport(classFqn = fqn)
  }
  else if (static) {
    val (qualifierFqn, name) = getPackageAndShortName(fqn)
    val importedName = importedName ?: return null
    if (qualifierFqn.isEmpty()) {
      RegularImport(
        classFqn = name,
        name = importedName
      )
    }
    else {
      StaticImport(
        classFqn = qualifierFqn,
        memberName = name,
        name = importedName
      )
    }
  }
  else if (star) {
    StarImport(packageFqn = fqn)
  }
  else {
    RegularImport(
      classFqn = fqn,
      name = importedName ?: return null
    )
  }
}
