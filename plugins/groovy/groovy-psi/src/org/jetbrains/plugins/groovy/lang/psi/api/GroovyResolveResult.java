// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface GroovyResolveResult extends ResolveResult {

  GroovyResolveResult[] EMPTY_ARRAY = new GroovyResolveResult[0];

  boolean isAccessible();

  boolean isStaticsOK();

  boolean isApplicable();

  @Nullable
  PsiElement getCurrentFileResolveContext();

  @NotNull
  PsiSubstitutor getContextSubstitutor();

  @NotNull
  PsiSubstitutor getSubstitutor();

  boolean isInvokedOnProperty();

  @Nullable
  SpreadState getSpreadState();
}
