// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import it.unimi.dsi.fastutil.Hash
import org.jetbrains.plugins.groovy.lang.resolve.imports.RegularImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport

internal object RegularImportHashingStrategy : Hash.Strategy<RegularImport> {
  override fun equals(o1: RegularImport?, o2: RegularImport?): Boolean {
    if (o1 === o2) return true
    if (o1 == null || o2 == null) return false
    return o1.classFqn == o2.classFqn && o1.name == o2.name
  }

  override fun hashCode(o: RegularImport?): Int = if (o == null) 0 else 31 * o.classFqn.hashCode() + o.name.hashCode()
}

internal object StarImportHashingStrategy : Hash.Strategy<StarImport> {
  override fun equals(o1: StarImport?, o2: StarImport?): Boolean {
    if (o1 === o2) return true
    if (o1 == null || o2 == null) return false
    return o1.packageFqn == o2.packageFqn
  }

  override fun hashCode(o: StarImport?): Int = o?.packageFqn?.hashCode() ?: 0
}
