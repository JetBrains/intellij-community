// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument;

import java.util.List;

@ApiStatus.Experimental
public abstract class GroovyApplicabilityProvider {

  public static final ExtensionPointName<GroovyApplicabilityProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.applicabilityProvider");

  /**
   * @return null if provider could not be applied in this case
   */
  public abstract @Nullable Applicability isApplicable(@NotNull List<Argument> arguments, @NotNull PsiMethod method);

  public static @Nullable Applicability checkProviders(@NotNull List<Argument> arguments, @NotNull PsiMethod method) {
    for (GroovyApplicabilityProvider applicabilityProvider : EP_NAME.getExtensions()) {
      Applicability result = applicabilityProvider.isApplicable(arguments, method);
      if (result != null) return result;
    }
    return null;
  }

  @Deprecated(forRemoval = true)
  public static @Nullable Applicability checkProviders(PsiType @NotNull [] argumentTypes, @NotNull PsiMethod method) {
    return checkProviders(ContainerUtil.map(argumentTypes, JustTypeArgument::new), method);
  }
}
