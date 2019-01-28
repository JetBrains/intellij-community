// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil

interface Argument {

  val type: PsiType?

  val topLevelType: PsiType? get() = type

  val runtimeType: PsiType? get() = TypeConversionUtil.erasure(topLevelType)
}
