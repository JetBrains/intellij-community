// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Collection;

/**
 * Allows to extend maps with domain-known keys.
 */
public abstract class GroovyMapContentProvider {

  public static final ExtensionPointName<GroovyMapContentProvider> EP_NAME = ExtensionPointName.create("org.intellij.groovy.mapContentProvider");

  /**
   * Computes all possible keys for a Map, where {@code qualifier} is the qualifier expression for the reference,
   * and {@code resolve} is the element where this map resolves to.
   */
  protected Collection<String> getKeyVariants(@NotNull GrExpression qualifier, @Nullable PsiElement resolve) {
    throw new UnsupportedOperationException();
  }

  /**
   * Computes the type for a map key.
   */
  public @Nullable PsiType getValueType(@NotNull GrExpression qualifier, @Nullable PsiElement resolve, @NotNull String key) {
    return null;
  }

  @ApiStatus.Internal
  public static Collection<String> getKeyVariants(@NotNull GroovyMapContentProvider provider, @NotNull GrExpression qualifier, @Nullable PsiElement resolved) {
    return provider.getKeyVariants(qualifier, resolved);
  }
}
