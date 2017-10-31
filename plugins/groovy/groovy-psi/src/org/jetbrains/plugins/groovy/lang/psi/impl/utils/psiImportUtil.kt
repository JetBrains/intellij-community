// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PsiImportUtil")

package org.jetbrains.plugins.groovy.lang.psi.impl.utils

import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.imports.*
import org.jetbrains.plugins.groovy.lang.resolve.qualifiedReferenceName

internal fun GrImportStatement.createImportFromStatement(): GroovyImport? {
  val reference = importReference ?: return null
  val static = isStatic
  val star = isOnDemand
  return if (static && star) {
    StaticStarImport(
      this,
      classFqn = reference.qualifiedReferenceName ?: return null
    )
  }
  else if (static) {
    val qualifier = reference.qualifier
    val name = reference.referenceName ?: return null
    val importedName = importedName ?: return null
    if (qualifier == null) {
      RegularImport(
        this,
        classFqn = name,
        name = importedName
      )
    }
    else {
      StaticImport(
        this,
        classFqn = qualifier.qualifiedReferenceName ?: return null,
        memberName = name,
        name = importedName
      )
    }
  }
  else if (star) {
    StarImport(
      this,
      packageFqn = reference.qualifiedReferenceName ?: return null
    )
  }
  else {
    RegularImport(
      this,
      classFqn = reference.qualifiedReferenceName ?: return null,
      name = importedName ?: return null
    )
  }
}
