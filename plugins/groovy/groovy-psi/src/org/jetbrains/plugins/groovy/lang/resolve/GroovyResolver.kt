// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

@Experimental
interface GroovyResolver<T : GroovyPolyVariantReference> : AbstractResolver<T, Collection<GroovyResolveResult>> {

  override fun resolve(ref: T, incomplete: Boolean): Collection<GroovyResolveResult>
}
