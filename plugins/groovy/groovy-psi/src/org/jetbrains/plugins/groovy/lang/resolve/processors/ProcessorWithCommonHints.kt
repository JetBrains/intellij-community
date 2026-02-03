// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints

abstract class ProcessorWithCommonHints : ProcessorWithHints() {

  protected fun nameHint(name: String) {
    hint(NameHint.KEY, NameHint { name })
  }

  protected fun elementClassHint(kind: DeclarationKind) {
    hint(ElementClassHint.KEY, ElementClassHint { it === kind })
  }
}
