// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession

class GroovyInferenceSession(typeParams: Array<PsiTypeParameter>,
                             val siteSubstitutor: PsiSubstitutor,
                             context: PsiElement,
                             val resolveMode: Boolean = true) : InferenceSession(typeParams, siteSubstitutor, context.manager, context) {

  fun result(): PsiSubstitutor {
    resolveBounds(myInferenceVariables, siteSubstitutor)
    return prepareSubstitution()
  }

  fun inferSubst(): PsiSubstitutor {
    repeatInferencePhases()
    return result()
  }
}
