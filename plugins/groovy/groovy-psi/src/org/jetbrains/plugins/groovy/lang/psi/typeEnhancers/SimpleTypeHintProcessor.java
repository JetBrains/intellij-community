// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SimpleTypeHintProcessor extends SignatureHintProcessor {
  @Override
  public String getHintName() {
    return "groovy.transform.stc.SimpleType";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull final PsiMethod method,
                                                 @NotNull PsiSubstitutor substitutor,
                                                 @NotNull String[] options) {
    return Collections.singletonList(ContainerUtil.map(options, value -> {
      try {
        return JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(value, method);
      }
      catch (IncorrectOperationException e) {
        return PsiType.NULL;
      }
    }, new PsiType[options.length]));
  }
}
