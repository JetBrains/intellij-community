// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class SignatureHintProcessor {
  private static final ExtensionPointName<SignatureHintProcessor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.signatureHintProcessor");

  static String[] buildOptions(PsiAnnotation anno) {
    PsiAnnotationMemberValue options = anno.findAttributeValue("options");
    ArrayList<String> result = new ArrayList<>();
    for (PsiAnnotationMemberValue initializer : AnnotationUtil.arrayAttributeValues(options)) {
      if (initializer instanceof PsiLiteral) {
        Object value = ((PsiLiteral)initializer).getValue();
        if (value instanceof String) {
          result.add((String)value);
        }
      }
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public abstract String getHintName();

  public abstract @NotNull List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                                   @NotNull PsiSubstitutor substitutor,
                                                                   String @NotNull [] options);

  public static @Nullable SignatureHintProcessor getHintProcessor(@NotNull String hint) {
    for (SignatureHintProcessor processor : EP_NAME.getExtensions()) {
      if (hint.equals(processor.getHintName())) {
        return processor;
      }
    }

    return null;
  }
}
