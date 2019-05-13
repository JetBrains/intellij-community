// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EmptySchemesManager extends SchemeManager<Object> {
  @Override
  @NotNull
  public Collection<Object> loadSchemes() {
    return Collections.emptySet();
  }

  @Override
  public void addScheme(@NotNull final Object scheme, final boolean replaceExisting) {
  }

  @Override
  @NotNull
  public List<Object> getAllSchemes() {
    return Collections.emptyList();
  }

  @Override
  public Object findSchemeByName(@NotNull String schemeName) {
    return null;
  }

  @Nullable
  @Override
  public String getCurrentSchemeName() {
    return null;
  }

  @Override
  public boolean removeScheme(@NotNull Object scheme) {
    return false;
  }

  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public File getRootDirectory() {
    //noinspection ConstantConditions
    return null;
  }

  @Override
  public void setCurrentSchemeName(@Nullable String schemeName, boolean notify) {
  }

  @Override
  public void setCurrentSchemeName(@Nullable String s) {
  }

  @Nullable
  @Override
  public Scheme getActiveScheme() {
    return null;
  }
}
