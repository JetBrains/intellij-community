// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public abstract class PersistentLibraryKind<P extends LibraryProperties> extends LibraryKind {
  /**
   * @param kindId must be unique among all {@link com.intellij.openapi.roots.libraries.LibraryType} and
   *               {@link com.intellij.openapi.roots.libraries.LibraryPresentationProvider} implementations.
   */
  public PersistentLibraryKind(@NotNull @NonNls String kindId) {
    super(kindId);
  }

  public abstract @NotNull P createDefaultProperties();

  public OrderRootType @NotNull [] getAdditionalRootTypes() {
    return new OrderRootType[0];
  }
}
