// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.impl.source.resolve.ResolveCache.AbstractResolver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

@ApiStatus.Internal
interface GroovyResolver<T : GroovyReference> : AbstractResolver<T, Array<GroovyResolveResult>> {

  override fun resolve(ref: T, incomplete: Boolean): Array<GroovyResolveResult>
}
