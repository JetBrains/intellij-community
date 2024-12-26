// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class FirstParamHintProcessor extends ParamHintProcessor {
  public FirstParamHintProcessor() {
    super("groovy.transform.stc.FirstParam", 0, -1);
  }

  public static final class FirstGeneric extends ParamHintProcessor {
    public FirstGeneric() {
      super("groovy.transform.stc.FirstParam.FirstGenericType", 0, 0);
    }
  }

  public static final class SecondGeneric extends ParamHintProcessor {
    public SecondGeneric() {
      super("groovy.transform.stc.FirstParam.SecondGenericType", 0, 1);
    }
  }

  public static final class ThirdGeneric extends ParamHintProcessor {
    public ThirdGeneric() {
      super("groovy.transform.stc.FirstParam.ThirdGenericType", 0, 2);
    }
  }

  public static final class Component extends SignatureHintProcessor {
    @Override
    public String getHintName() {
      return "groovy.transform.stc.FirstParam.Component";
    }

    @Override
    public @NotNull List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                            @NotNull PsiSubstitutor substitutor,
                                                            String @NotNull [] options) {
      List<PsiType[]> signatures = new FirstParamHintProcessor().inferExpectedSignatures(method, substitutor, options);
      if (signatures.size() == 1) {
        PsiType[] signature = signatures.get(0);
        if (signature.length == 1) {
          PsiType type = signature[0];
          if (type instanceof PsiArrayType) {
            return produceResult(((PsiArrayType)type).getComponentType());
          }
        }
      }
      return Collections.emptyList();
    }
  }
}
