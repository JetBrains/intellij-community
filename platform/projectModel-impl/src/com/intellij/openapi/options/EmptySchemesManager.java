// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class EmptySchemesManager extends SchemeManager<Scheme> {
  @Override
  @NotNull
  public Collection<Scheme> loadSchemes() {
    return Collections.emptySet();
  }

  @Override
  public void addNewScheme(@NotNull final Scheme scheme, final boolean replaceExisting) {
  }

  @Override
  public void clearAllSchemes() {
  }

  @Override
  @NotNull
  public List<Scheme> getAllSchemes() {
    return Collections.emptyList();
  }

  @Override
  public Scheme findSchemeByName(@NotNull String schemeName) {
    return null;
  }

  @Nullable
  @Override
  public String getCurrentSchemeName() {
    return null;
  }

  @Override
  public boolean removeScheme(@NotNull Scheme scheme) {
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
  public Scheme getCurrentScheme() {
    return null;
  }

  @Override
  public void setCurrentScheme(@Nullable Scheme scheme) {
  }
}
