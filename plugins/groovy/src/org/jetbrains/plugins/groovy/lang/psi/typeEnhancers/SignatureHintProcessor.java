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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
public abstract class SignatureHintProcessor {
  private static final ExtensionPointName<SignatureHintProcessor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.signatureHintProcessor");

  public abstract String getHintName();

  @NotNull
  public abstract List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                          @NotNull PsiSubstitutor substitutor,
                                                          @Nullable PsiAnnotationMemberValue options);

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
