/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult;

public abstract class GroovyApplicabilityProvider {

  public static final ExtensionPointName<GroovyApplicabilityProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.applicabilityProvider");

  /**
   * @return null if provider could not be applied in this case
   */
  @Nullable
  public abstract ApplicabilityResult isApplicable(@NotNull PsiType[] argumentTypes,
                                                   @NotNull PsiMethod method,
                                                   @Nullable PsiSubstitutor substitutor,
                                                   @Nullable PsiElement place,
                                                   final boolean eraseParameterTypes);

  @Nullable
  public static ApplicabilityResult checkProviders(@NotNull PsiType[] argumentTypes,
                                                   @NotNull PsiMethod method,
                                                   @Nullable PsiSubstitutor substitutor,
                                                   @Nullable PsiElement place,
                                                   final boolean eraseParameterTypes) {
    for (GroovyApplicabilityProvider applicabilityProvider : EP_NAME.getExtensions()) {
      ApplicabilityResult result = applicabilityProvider.isApplicable(argumentTypes, method, substitutor, place, eraseParameterTypes);
      if (result != null) return result;
    }
    return null;
  }
}
