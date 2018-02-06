// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint;

public class GroovyInferenceSession extends InferenceSession {
  public GroovyInferenceSession(PsiTypeParameter[] typeParams,
                                PsiSubstitutor siteSubstitutor,
                                PsiManager manager, PsiElement context) {
    super(typeParams, siteSubstitutor, manager, context);
  }


  public void addConstraint(PsiType leftType, PsiType rightType) {
    final PsiType right = getSiteSubstitutor().substitute(rightType);
    PsiType t = substituteWithInferenceVariables(leftType);
    PsiType s = substituteWithInferenceVariables(right);
    if (t != null && s != null) {
      addConstraint(new TypeCompatibilityConstraint(t, s));
    }
  }

  public boolean doInfer() {
    return repeatInferencePhases();
  }

  public PsiSubstitutor result() {
    resolveBounds(myInferenceVariables, getSiteSubstitutor());
    return prepareSubstitution();
  }

}
