/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class SignatureHintProcessor {
  private static final ExtensionPointName<SignatureHintProcessor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.signatureHintProcessor");

  static String[] buildOptions(PsiAnnotation anno) {
    PsiAnnotationMemberValue options = anno.findAttributeValue("options");
    ArrayList<String> result = ContainerUtil.newArrayList();
    for (PsiAnnotationMemberValue initializer : AnnotationUtil.arrayAttributeValues(options)) {
      if (initializer instanceof PsiLiteral) {
        Object value = ((PsiLiteral)initializer).getValue();
        if (value instanceof String) {
          result.add((String)value);
        }
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  public abstract String getHintName();

  @NotNull
  public abstract List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                          @NotNull PsiSubstitutor substitutor,
                                                          @NotNull String[] options);

  @Nullable
  public static SignatureHintProcessor getHintProcessor(@NotNull String hint) {
    for (SignatureHintProcessor processor : EP_NAME.getExtensions()) {
      if (hint.equals(processor.getHintName())) {
        return processor;
      }
    }

    return null;
  }
}
