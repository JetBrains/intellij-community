// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import org.jetbrains.plugins.groovy.lang.resolve.imports.*

// T of ImportKind
internal sealed class ImportKind<T : GroovyImport> {
  internal object Regular : ImportKind<RegularImport>()
  internal object Static : ImportKind<StaticImport>()
  internal object Star : ImportKind<StarImport>()
  internal object StaticStar : ImportKind<StaticStarImport>()
}
