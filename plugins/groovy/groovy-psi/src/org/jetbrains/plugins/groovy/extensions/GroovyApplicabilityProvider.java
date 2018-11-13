// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult;

@ApiStatus.Experimental
public abstract class GroovyApplicabilityProvider {

  public static final ExtensionPointName<GroovyApplicabilityProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.applicabilityProvider");

  /**
   * @return null if provider could not be applied in this case
   */
  @Nullable
  public abstract ApplicabilityResult isApplicable(@NotNull PsiType[] argumentTypes, @NotNull PsiMethod method);

  @Nullable
  public static ApplicabilityResult checkProviders(@NotNull PsiType[] argumentTypes, @NotNull PsiMethod method) {
    for (GroovyApplicabilityProvider applicabilityProvider : EP_NAME.getExtensions()) {
      ApplicabilityResult result = applicabilityProvider.isApplicable(argumentTypes, method);
      if (result != null) return result;
    }
    return null;
  }
}
