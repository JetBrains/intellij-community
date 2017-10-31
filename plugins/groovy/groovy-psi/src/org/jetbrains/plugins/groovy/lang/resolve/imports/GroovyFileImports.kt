// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

interface GroovyFileImports {

  val regularImports: Collection<RegularImport>

  val staticImports: Collection<StaticImport>

  val starImports: Collection<StarImport>

  val staticStarImports: Collection<StaticStarImport>


  val allImports: Collection<GroovyImport>

  val allNamedImports: Collection<GroovyNamedImport>

  val allStarImports: Collection<GroovyStarImport>
}
