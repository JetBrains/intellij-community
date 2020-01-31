// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType

interface CallParameter {

  val type: PsiType?

  val parameterName: String?

  val isOptional: Boolean
}
