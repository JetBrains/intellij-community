// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement

@Experimental
interface GroovyElementFilter {

  /**
   * @return `true` if [element] is not really an element,
   * meaning it would be transformed into something else,
   * otherwise `false`
   */
  fun isFake(element: GroovyPsiElement): Boolean
}
