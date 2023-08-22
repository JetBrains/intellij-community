// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract P createDefaultProperties();

  public OrderRootType @NotNull [] getAdditionalRootTypes() {
    return new OrderRootType[0];
  }
}
