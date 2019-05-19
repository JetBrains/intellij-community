// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil

class ErasedArgument(original: Argument) : Argument {

  override val type: PsiType? by lazy {
    TypeConversionUtil.erasure(original.topLevelType)
  }
}
