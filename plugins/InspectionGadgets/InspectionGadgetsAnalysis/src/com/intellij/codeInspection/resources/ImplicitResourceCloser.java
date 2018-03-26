/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.resources;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

public interface ImplicitResourceCloser {
  ExtensionPointName<ImplicitResourceCloser> EP_NAME = new ExtensionPointName<>("com.intellij.implicit.resource.closer");

  /**
   * Method used to understand if {@link AutoCloseable} variable closed properly.
   * This extension point may be useful for framework, that provides additional ways to close AutoCloseables, like Lombok.
   * @param variable {@link AutoCloseable} variable to check
   * @return true if variable closed properly
   */
  boolean isSafelyClosed(@NotNull PsiVariable variable);
}
