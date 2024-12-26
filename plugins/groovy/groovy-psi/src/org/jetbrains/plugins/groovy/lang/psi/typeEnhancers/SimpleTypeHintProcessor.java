// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class SimpleTypeHintProcessor extends SignatureHintProcessor {
  @Override
  public String getHintName() {
    return "groovy.transform.stc.SimpleType";
  }

  @Override
  public @NotNull List<PsiType[]> inferExpectedSignatures(final @NotNull PsiMethod method,
                                                          @NotNull PsiSubstitutor substitutor,
                                                          String @NotNull [] options) {
    return Collections.singletonList(ContainerUtil.map(options, value -> {
      try {
        return JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(value, method);
      }
      catch (IncorrectOperationException e) {
        return PsiTypes.nullType();
      }
    }, new PsiType[options.length]));
  }
}
