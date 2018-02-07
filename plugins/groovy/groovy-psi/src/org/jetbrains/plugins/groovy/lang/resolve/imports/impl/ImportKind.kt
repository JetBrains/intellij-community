// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import org.jetbrains.plugins.groovy.lang.resolve.imports.*

@kotlin.Suppress("unused") // T of ImportKind
internal sealed class ImportKind<T : GroovyImport> {
  internal object Regular : ImportKind<RegularImport>()
  internal object Static : ImportKind<StaticImport>()
  internal object Star : ImportKind<StarImport>()
  internal object StaticStar : ImportKind<StaticStarImport>()
}
