// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

interface GrSingleResultResolverProcessor<out T : GroovyResolveResult> : GrResolverProcessor<T> {

  val result: T?

  override val results: List<T> get() = result?.let { listOf(it) } ?: emptyList()
}
