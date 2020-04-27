// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType

interface GroovyMethodCandidate {

  val receiverType: PsiType?

  val method: PsiMethod

  val argumentMapping: ArgumentMapping<PsiCallParameter>?
}
