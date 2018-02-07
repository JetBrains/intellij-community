// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.impl.source.resolve.ResolveCache.PolyVariantResolver
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

/**
 * Wrapper returning an array of GroovyResolveResult
 */
internal object GrCodeReferencePolyVariantResolver : PolyVariantResolver<GrCodeReferenceElement> {

  override fun resolve(t: GrCodeReferenceElement, incompleteCode: Boolean): Array<GroovyResolveResult> {
    return ContainerUtil.toArray(GrCodeReferenceResolver.resolve(t, incompleteCode), GroovyResolveResult.EMPTY_ARRAY)
  }
}
